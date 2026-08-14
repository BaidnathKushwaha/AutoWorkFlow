package com.autoworkflow.integration;

import com.autoworkflow.common.enums.IntegrationStatus;
import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.integration.dto.IntegrationResponse;
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

    private final IntegrationRepository integrationRepository;
    private final EncryptionUtils encryptionUtils;

    /** Returns one card per known provider: connected ones with real status, others as disconnected stubs. */
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

        // NOTE: this deliberately does NOT make a live test call to the provider (e.g. an
        // actual OpenAI/Gemini chat request) to verify the key works before saving. Doing so
        // would spend the user's API quota just to connect an integration, which directly
        // conflicts with keeping AutoWorkflow testable without burning AI credits. The
        // trade-off: a syntactically-valid-looking but actually-invalid key will show as
        // "Healthy" here and only surface as a clear "AI provider not connected" /
        // AiProviderException AUTH_FAILED error the first time a workflow node actually
        // calls it. This is a known, documented limitation — see the architecture report.
        Integration integration = integrationRepository.findByUserIdAndProvider(userId, provider)
                .orElse(Integration.builder().userId(userId).provider(provider).build());

        integration.setEncryptedAccessToken(encryptionUtils.encrypt(accessToken));
        if (refreshToken != null) {
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

    /** Used by node execution strategies to fetch a usable, decrypted access token for a provider. */
    public String getDecryptedAccessToken(UUID userId, String provider) {
        Integration integration = integrationRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No connected " + provider + " integration for this user. Connect it on the Integrations page first."));

        if (integration.getStatus() != IntegrationStatus.HEALTHY) {
            throw new ResourceNotFoundException("The " + provider + " integration is not healthy. Reconnect it on the Integrations page.");
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

    private void validateProvider(String provider) {
        if (provider == null || !IntegrationProviderCatalog.ALL_PROVIDERS.contains(provider)) {
            throw new IntegrationException("Unknown integration provider: " + provider
                    + ". Supported providers: " + IntegrationProviderCatalog.ALL_PROVIDERS);
        }
    }
}
