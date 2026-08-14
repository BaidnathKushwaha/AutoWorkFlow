package com.autoworkflow.integration;

import com.autoworkflow.common.enums.IntegrationStatus;
import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.integration.dto.IntegrationResponse;
import com.autoworkflow.util.EncryptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies OpenRouter is wired into the EXISTING integration/credential mechanism —
 * no second storage system was introduced. IntegrationService itself required no
 * behavioral changes: saveTokens/disconnect/getDecryptedAccessToken/listForUser are
 * all already generic over the provider string. Only IntegrationProviderCatalog's
 * static list needed "openrouter" added so validateProvider() accepts it (this is
 * what lets a user actually connect a key through the existing
 * POST /api/integrations/key/{provider} endpoint) and so it appears in listForUser's
 * per-provider stub list the Integrations UI renders from.
 */
class IntegrationServiceTest {

    private IntegrationRepository repository;
    private IntegrationService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationRepository.class);
        // Real EncryptionUtils (not mocked) so tests exercise an actual encrypt/decrypt
        // round-trip, not just "some string got stored somewhere".
        EncryptionUtils encryptionUtils = new EncryptionUtils("test-secret-at-least-32-bytes-long!!");
        service = new IntegrationService(repository, encryptionUtils);
        when(repository.save(any(Integration.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void openrouterIsAValidProvider_catalogAcceptsIt() {
        when(repository.findByUserIdAndProvider(userId, "openrouter")).thenReturn(Optional.empty());

        Integration saved = service.saveTokens(userId, "openrouter", "sk-or-test-key", null,
                "API Key", List.of("API Access"), null);

        assertThat(saved.getProvider()).isEqualTo("openrouter");
        assertThat(saved.getStatus()).isEqualTo(IntegrationStatus.HEALTHY);
        // The raw key must never be stored in plaintext — this is the whole point of
        // reusing the existing encrypted-storage mechanism instead of a new one.
        assertThat(saved.getEncryptedAccessToken()).isNotEqualTo("sk-or-test-key");
    }

    @Test
    void savedOpenrouterKey_decryptsBackToOriginalPlaintext() {
        when(repository.findByUserIdAndProvider(userId, "openrouter")).thenReturn(Optional.empty());
        Integration saved = service.saveTokens(userId, "openrouter", "sk-or-my-real-key", null,
                "API Key", List.of("API Access"), null);

        // getDecryptedAccessToken looks the row up again; simulate that by returning the
        // same saved entity (with status HEALTHY, as saveTokens set it).
        when(repository.findByUserIdAndProvider(userId, "openrouter")).thenReturn(Optional.of(saved));

        String decrypted = service.getDecryptedAccessToken(userId, "openrouter");
        assertThat(decrypted).isEqualTo("sk-or-my-real-key");
    }

    @Test
    void disconnectingOpenrouter_worksLikeAnyOtherProvider() {
        Integration existing = Integration.builder().id(UUID.randomUUID()).userId(userId).provider("openrouter").build();
        when(repository.findByUserIdAndProvider(userId, "openrouter")).thenReturn(Optional.of(existing));

        service.disconnect(userId, "openrouter");

        verify(repository).delete(existing);
    }

    @Test
    void unknownProvider_stillRejected_catalogAdditionDidNotMakeValidationPermissive() {
        assertThatThrownBy(() -> service.saveTokens(userId, "not_a_real_provider", "key", null,
                "API Key", List.of("API Access"), null))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("not_a_real_provider");
    }

    @Test
    void listForUser_includesOpenrouterAsADisconnectedStub_whenNothingConnected() {
        when(repository.findByUserId(userId)).thenReturn(List.of());

        List<IntegrationResponse> result = service.listForUser(userId);

        IntegrationResponse openrouter = result.stream()
                .filter(r -> r.provider().equals("openrouter"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("openrouter missing from listForUser() — Integrations UI would never show it"));
        assertThat(openrouter.status()).isEqualTo("DISCONNECTED");
    }

    @Test
    void listForUser_stillIncludesExistingProviders_openaiAndGeminiUnaffected() {
        when(repository.findByUserId(userId)).thenReturn(List.of());

        List<IntegrationResponse> result = service.listForUser(userId);
        List<String> providers = result.stream().map(IntegrationResponse::provider).toList();

        assertThat(providers).contains("openai", "gemini", "openrouter", "github", "slack");
    }

    @Test
    void noOpenrouterConnection_getDecryptedAccessToken_throwsResourceNotFound_notSomeOtherError() {
        when(repository.findByUserIdAndProvider(userId, "openrouter")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDecryptedAccessToken(userId, "openrouter"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
