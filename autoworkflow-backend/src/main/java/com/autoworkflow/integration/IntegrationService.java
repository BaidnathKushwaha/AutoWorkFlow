package com.autoworkflow.integration;

import com.autoworkflow.common.enums.IntegrationStatus;
import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.integration.dto.IntegrationResponse;
import com.autoworkflow.integration.oauth.GoogleTokenExchange;
import com.autoworkflow.integration.oauth.OAuthToken;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeClient;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeRegistry;
import com.autoworkflow.util.EncryptionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntegrationService {

    private static final long REFRESH_SKEW_SECONDS = 60;

    private final IntegrationRepository integrationRepository;
    private final EncryptionUtils encryptionUtils;
    private final OAuthTokenExchangeRegistry oauthTokenExchangeRegistry;

    public List<IntegrationResponse> listForUser(UUID userId) {
        Map<String, Integration> connected = integrationRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Integration::getProvider, i -> i));
        return IntegrationProviderCatalog.ALL_PROVIDERS.stream()
                .map(provider -> connected.containsKey(provider)
                        ? IntegrationResponse.from(connected.get(provider))
                        : IntegrationResponse.disconnectedStub(provider, IntegrationProviderCatalog.DEFAULT_SCOPES.get(provider)))
                .collect(Collectors.toList());
    }

    @Transactional
    public Integration saveTokens(UUID userId, String provider, String accessToken, String refreshToken,
                                   String accountLabel, List<String> scopes, Instant expiresAt) {
        validateProvider(provider);
        Integration integration = integrationRepository.findByUserIdAndProvider(userId, provider)
                .orElse(Integration.builder().userId(userId).provider(provider).build());

        integration.setEncryptedAccessToken(encryptionUtils.encrypt(accessToken));
        if (refreshToken != null && !refreshToken.isBlank()) {
            integration.setEncryptedRefreshToken(encryptionUtils.encrypt(refreshToken));
        }
        integration.setAccountLabel(accountLabel);
        integration.setScopes(scopes);
        integration.setStatus(IntegrationStatus.HEALTHY);
        integration.setLastCheckedAt(Instant.now());
        integration.setTokenExpiresAt(expiresAt);
        return integrationRepository.save(integration);
    }

    @Transactional
    public void disconnect(UUID userId, String provider) {
        validateProvider(provider);
        Integration integration = integrationRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException("Integration not connected: " + provider));
        integrationRepository.delete(integration);
    }

    /** Returns a usable access token and refreshes Google credentials when they are expired or near expiry. */
    @Transactional
    public String getDecryptedAccessToken(UUID userId, String provider) {
        Integration integration = integrationRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No connected " + provider + " integration for this user. Connect it on the Integrations page first."));

        if (integration.getStatus() != IntegrationStatus.HEALTHY) {
            throw new ResourceNotFoundException("The " + provider + " integration is not healthy. Reconnect it on the Integrations page.");
        }

        if (isNearExpiry(integration.getTokenExpiresAt())) {
            if (!isGoogleMailProvider(provider) || integration.getEncryptedRefreshToken() == null) {
                markError(integration);
                throw new IntegrationException("The " + provider + " access token has expired. Reconnect the integration.");
            }
            try {
                String refreshToken = encryptionUtils.decrypt(integration.getEncryptedRefreshToken());
                OAuthToken token = ((GoogleTokenExchange) oauthTokenExchangeRegistry.resolve(provider)).refresh(refreshToken);
                integration.setEncryptedAccessToken(encryptionUtils.encrypt(token.accessToken()));
                if (token.refreshToken() != null && !token.refreshToken().isBlank()) {
                    integration.setEncryptedRefreshToken(encryptionUtils.encrypt(token.refreshToken()));
                }
                integration.setTokenExpiresAt(token.expiresAt());
                integration.setStatus(IntegrationStatus.HEALTHY);
                integration.setLastCheckedAt(Instant.now());
                integrationRepository.save(integration);
            } catch (Exception e) {
                markError(integration);
                throw new IntegrationException("The " + provider + " access token could not be refreshed. Reconnect the integration.", e);
            }
        }
        return encryptionUtils.decrypt(integration.getEncryptedAccessToken());
    }

    @Transactional
    public void markStatus(UUID integrationId, IntegrationStatus status) {
        integrationRepository.findById(integrationId).ifPresent(i -> {
            i.setStatus(status);
            i.setLastCheckedAt(Instant.now());
            integrationRepository.save(i);
        });
    }

    private boolean isNearExpiry(Instant expiresAt) {
        return expiresAt != null && expiresAt.isBefore(Instant.now().plusSeconds(REFRESH_SKEW_SECONDS));
    }

    private boolean isGoogleMailProvider(String provider) {
        return "gmail".equals(provider) || "google_sheets".equals(provider);
    }

    private void markError(Integration integration) {
        integration.setStatus(IntegrationStatus.ERROR);
        integration.setLastCheckedAt(Instant.now());
        integrationRepository.save(integration);
    }

    private void validateProvider(String provider) {
        if (provider == null || !IntegrationProviderCatalog.ALL_PROVIDERS.contains(provider)) {
            throw new IntegrationException("Unknown integration provider: " + provider
                    + ". Supported providers: " + IntegrationProviderCatalog.ALL_PROVIDERS);
        }
    }
}
