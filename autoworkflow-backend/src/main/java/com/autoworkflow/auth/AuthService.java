package com.autoworkflow.auth;

import com.autoworkflow.auth.dto.*;
import com.autoworkflow.common.enums.UserRole;
import com.autoworkflow.common.exception.DuplicateResourceException;
import com.autoworkflow.common.exception.UnauthorizedException;
import com.autoworkflow.security.jwt.JwtService;
import com.autoworkflow.security.user.CustomUserDetails;
import com.autoworkflow.user.User;
import com.autoworkflow.user.UserRepository;
import com.autoworkflow.user.dto.UserResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.google.login.client-id:}")
    private String googleLoginClientId;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .build();
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        return issueTokens(user);
    }

    /**
     * Authenticates a user via Google ID token.
     * <p>
     * Logic:
     * 1. Verify the ID token with Google's public keys.
     * 2. Extract email, name, googleId (sub), avatarUrl from token payload.
     * 3. If a user with that googleId already exists → log in directly.
     * 4. If a user with that email exists (manual signup) → link the Google account and log in.
     * 5. Otherwise → create a new user (no password) and log in.
     */
    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyGoogleToken(request.idToken());

        String googleId  = payload.getSubject();
        String email     = payload.getEmail().toLowerCase();
        String name      = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        // 1. Existing user already linked to this Google account
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User user = byGoogleId.get();
            // Refresh avatar in case it changed
            user.setAvatarUrl(avatarUrl);
            user = userRepository.save(user);
            return issueTokens(user);
        }

        // 2. User signed up manually with the same email → link Google account
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setGoogleId(googleId);
            user.setAvatarUrl(avatarUrl);
            user = userRepository.save(user);
            return issueTokens(user);
        }

        // 3. Brand-new user — create account without password
        final String finalName = name;
        User newUser = User.builder()
                .name(finalName)
                .email(email)
                .googleId(googleId)
                .avatarUrl(avatarUrl)
                .role(UserRole.USER)
                .build();
        newUser = userRepository.save(newUser);
        return issueTokens(newUser);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked. Please log in again.");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        // rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private GoogleIdToken.Payload verifyGoogleToken(String idToken) {
        if (googleLoginClientId == null || googleLoginClientId.isBlank()) {
            log.error("Google login failed: app.google.login.client-id is not configured on the backend.");
            throw new UnauthorizedException("Google login is not configured on the server.");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setIssuers(java.util.Arrays.asList("accounts.google.com", "https://accounts.google.com"))
                .setAudience(Collections.singletonList(googleLoginClientId))
                .build();

        GoogleIdToken unverifiedToken;
        try {
            unverifiedToken = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), idToken);
        } catch (Exception parseEx) {
            log.warn("Google ID token parsing failed: {}", parseEx.getMessage());
            throw new UnauthorizedException("Google ID token malformed: Unable to parse JWT structure.");
        }

        if (unverifiedToken == null || unverifiedToken.getPayload() == null) {
            throw new UnauthorizedException("Google ID token payload is empty.");
        }

        GoogleIdToken.Payload payload = unverifiedToken.getPayload();

        // 1. Issuer check
        String issuer = payload.getIssuer();
        boolean issOk = "accounts.google.com".equals(issuer) || "https://accounts.google.com".equals(issuer);
        if (!issOk) {
            log.warn("Google ID token verification failed - Issuer mismatch: {}", issuer);
            throw new UnauthorizedException("Google ID token issuer mismatch: Expected 'accounts.google.com' or 'https://accounts.google.com', got '" + issuer + "'");
        }

        // 2. Audience check
        String tokenAud = payload.getAudience() != null ? payload.getAudience().toString() : "";
        boolean audOk = googleLoginClientId.equals(tokenAud) || (payload.getAudienceAsList() != null && payload.getAudienceAsList().contains(googleLoginClientId));
        if (!audOk) {
            log.warn("Google ID token verification failed - Audience mismatch: token aud '{}', backend client-id '{}'", tokenAud, googleLoginClientId);
            throw new UnauthorizedException("Google ID token audience mismatch: Token audience '" + tokenAud + "' does not match backend client ID '" + googleLoginClientId + "'");
        }

        // 3. Expiration / Clock Skew check
        long nowSec = System.currentTimeMillis() / 1000L;
        Long expSec = payload.getExpirationTimeSeconds();
        boolean timeOk = expSec != null && expSec > (nowSec - 600);
        if (!timeOk) {
            log.warn("Google ID token verification failed - Token expired: exp '{}', now '{}'", expSec, nowSec);
            throw new UnauthorizedException("Google ID token expired: Expiration timestamp " + expSec + " is earlier than current server time " + nowSec);
        }

        // 4. Verification via GoogleIdTokenVerifier with fallback to Google TokenInfo API
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken != null) {
                return googleIdToken.getPayload();
            }
            log.warn("Local GoogleIdTokenVerifier returned null for kid '{}'. Falling back to Google TokenInfo endpoint...",
                    unverifiedToken.getHeader().getKeyId());
        } catch (Exception localVerifyEx) {
            log.warn("Local GoogleIdTokenVerifier check failed: {}. Falling back to Google TokenInfo endpoint...", localVerifyEx.getMessage());
        }

        // Fallback: Verify directly via Google's official TokenInfo API endpoint
        try {
            org.springframework.web.client.RestClient restClient = org.springframework.web.client.RestClient.create();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> tokenInfo = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                    .retrieve()
                    .body(java.util.Map.class);

            if (tokenInfo == null || tokenInfo.get("email") == null) {
                throw new UnauthorizedException("Google ID token verification failed via Google TokenInfo API.");
            }

            String tokenAudience = (String) tokenInfo.get("aud");
            String tokenAzp = (String) tokenInfo.get("azp");
            boolean tokenAudMatch = googleLoginClientId.equals(tokenAudience) || googleLoginClientId.equals(tokenAzp);

            if (!tokenAudMatch) {
                log.warn("Google TokenInfo verification failed - Audience mismatch: aud '{}', azp '{}', expected '{}'",
                        tokenAudience, tokenAzp, googleLoginClientId);
                throw new UnauthorizedException("Google ID token audience mismatch: Token audience does not match backend client ID.");
            }

            GoogleIdToken.Payload verifiedPayload = new GoogleIdToken.Payload();
            verifiedPayload.setSubject((String) tokenInfo.get("sub"));
            verifiedPayload.setEmail((String) tokenInfo.get("email"));
            if (tokenInfo.get("name") != null) {
                verifiedPayload.set("name", tokenInfo.get("name"));
            }
            if (tokenInfo.get("picture") != null) {
                verifiedPayload.set("picture", tokenInfo.get("picture"));
            }
            log.info("Google ID token verified successfully via Google TokenInfo API for email: {}", tokenInfo.get("email"));
            return verifiedPayload;
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception remoteEx) {
            log.error("Google TokenInfo API verification failed: {}", remoteEx.getMessage(), remoteEx);
            throw new UnauthorizedException("Google ID token verification failed: " + remoteEx.getMessage());
        }
    }

    private AuthResponse issueTokens(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        String accessToken = jwtService.generateAccessToken(principal, user.getId());
        String refreshToken = jwtService.generateRefreshToken(principal, user.getId());

        RefreshToken entity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshToken))
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(entity);

        return AuthResponse.of(accessToken, refreshToken, UserResponse.from(user));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
