package com.autoworkflow.user;

import com.autoworkflow.common.exception.InvalidAiPreferenceException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPreferenceServicePhase1ContractTest {

    @Test
    void autoUpdate_clearsProviderAndModel() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, AiMode.SPECIFIC, "openrouter", "google/gemini-2.5-flash");
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(user));

        AiPreferenceService service = new AiPreferenceService(repository);
        var response = service.update(
                userId,
                new com.autoworkflow.user.dto.AiPreferenceUpdateRequest(
                        AiMode.AUTO,
                        null,
                        null
                )
        );

        assertThat(response.mode()).isEqualTo(AiMode.AUTO);
        assertThat(response.provider()).isNull();
        assertThat(response.model()).isNull();
        assertThat(user.getAiMode()).isEqualTo(AiMode.AUTO);
        assertThat(user.getAiProvider()).isNull();
        assertThat(user.getAiModel()).isNull();
        verify(repository).save(user);
    }

    @Test
    void specificUpdate_requiresProviderAndModel() {
        UUID userId = UUID.randomUUID();
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(user(userId, AiMode.AUTO, null, null)));

        AiPreferenceService service = new AiPreferenceService(repository);

        assertThatThrownBy(() -> service.update(
                userId,
                new com.autoworkflow.user.dto.AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        null,
                        null
                )
        )).isInstanceOf(InvalidAiPreferenceException.class);
    }

    @Test
    void specificUpdate_persistsExactSupportedProviderAndModel() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, AiMode.AUTO, null, null);
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(user));

        AiPreferenceService service = new AiPreferenceService(repository);
        var response = service.update(
                userId,
                new com.autoworkflow.user.dto.AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "openrouter",
                        "google/gemini-2.5-flash"
                )
        );

        assertThat(response.mode()).isEqualTo(AiMode.SPECIFIC);
        assertThat(response.provider()).isEqualTo("openrouter");
        assertThat(response.model()).isEqualTo("google/gemini-2.5-flash");
        assertThat(user.getAiProvider()).isEqualTo("openrouter");
        assertThat(user.getAiModel()).isEqualTo("google/gemini-2.5-flash");
    }

    @Test
    void autoResolution_returnsNullProviderAndModel() {
        UUID userId = UUID.randomUUID();
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(user(userId, AiMode.AUTO, "openrouter", "google/gemini-2.5-flash")));

        AiPreferenceService service = new AiPreferenceService(repository);
        var resolved = service.resolveForUser(userId);

        assertThat(resolved.mode()).isEqualTo(AiMode.AUTO);
        assertThat(resolved.provider()).isNull();
        assertThat(resolved.model()).isNull();
    }

    private User user(UUID id, AiMode mode, String provider, String model) {
        return User.builder()
                .id(id)
                .name("Phase 1 User")
                .email("phase1-" + id + "@example.com")
                .aiMode(mode)
                .aiProvider(provider)
                .aiModel(model)
                .build();
    }
}
