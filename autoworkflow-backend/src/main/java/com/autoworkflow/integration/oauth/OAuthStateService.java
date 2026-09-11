package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthStateService {
    private static final long STATE_TTL_SECONDS = 600;
    private final OAuthStateRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String create(UUID userId, String provider) {
        cleanupExpired();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(OAuthState.builder()
                .stateHash(hash(state))
                .userId(userId)
                .provider(provider)
                .expiresAt(Instant.now().plusSeconds(STATE_TTL_SECONDS))
                .build());
        return state;
    }

    @Transactional
    public UUID consume(String state, String provider) {
        if (state == null || state.isBlank()) {
            throw new IntegrationException("OAuth authorization state is missing.");
        }
        OAuthState record = repository.findByStateHash(hash(state))
                .orElseThrow(() -> new IntegrationException("OAuth authorization state is invalid or expired."));
        if (!record.getProvider().equals(provider)) {
            throw new IntegrationException("OAuth authorization state does not match the requested provider.");
        }
        if (record.getUsedAt() != null) {
            throw new IntegrationException("OAuth authorization state has already been used.");
        }
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new IntegrationException("OAuth authorization state is invalid or expired.");
        }
        record.setUsedAt(Instant.now());
        repository.save(record);
        return record.getUserId();
    }

    private void cleanupExpired() {
        repository.findAll().stream()
                .filter(s -> s.getExpiresAt().isBefore(Instant.now().minusSeconds(60)) || s.getUsedAt() != null)
                .forEach(repository::delete);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create OAuth state", e);
        }
    }
}
