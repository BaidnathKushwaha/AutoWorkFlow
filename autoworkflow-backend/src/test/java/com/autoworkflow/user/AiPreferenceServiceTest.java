package com.autoworkflow.user;

import com.autoworkflow.common.exception.InvalidAiPreferenceException;
import com.autoworkflow.user.dto.AiPreferenceUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiPreferenceServiceTest {

    private UserRepository userRepository;
    private AiPreferenceService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AiPreferenceService(userRepository);

        userId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .name("Test User")
                .email("test@example.com")
                .aiMode(AiMode.AUTO)
                .aiProvider(null)
                .aiModel(null)
                .build();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
    }

    @Test
    void newUserPreference_defaultsToAuto() {
        AiPreferenceService.ResolvedPreference result =
                service.resolveForUser(userId);

        assertThat(result.mode())
                .isEqualTo(AiMode.AUTO);

        assertThat(result.provider())
                .isNull();

        assertThat(result.model())
                .isNull();
    }

    @Test
    void specificPreference_persistsAndResolvesExactSelection() {
        AiPreferenceUpdateRequest request =
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "openrouter",
                        "google/gemini-2.5-flash"
                );

        service.update(userId, request);

        assertThat(user.getAiMode())
                .isEqualTo(AiMode.SPECIFIC);

        assertThat(user.getAiProvider())
                .isEqualTo("openrouter");

        assertThat(user.getAiModel())
                .isEqualTo("google/gemini-2.5-flash");

        AiPreferenceService.ResolvedPreference result =
                service.resolveForUser(userId);

        assertThat(result.mode())
                .isEqualTo(AiMode.SPECIFIC);

        assertThat(result.provider())
                .isEqualTo("openrouter");

        assertThat(result.model())
                .isEqualTo("google/gemini-2.5-flash");
    }

    @Test
    void specificWithoutProvider_isRejected() {
        AiPreferenceUpdateRequest request =
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        null,
                        "google/gemini-2.5-flash"
                );

        assertThatThrownBy(() ->
                service.update(userId, request)
        )
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void specificWithoutModel_isRejected() {
        AiPreferenceUpdateRequest request =
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "openrouter",
                        null
                );

        assertThatThrownBy(() ->
                service.update(userId, request)
        )
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("model");
    }

    @Test
    void unknownProvider_isRejected() {
        AiPreferenceUpdateRequest request =
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "unknown-provider",
                        "some-model"
                );

        assertThatThrownBy(() ->
                service.update(userId, request)
        )
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("Unsupported AI provider");
    }

    @Test
    void unsupportedModel_isRejected() {
        AiPreferenceUpdateRequest request =
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "openrouter",
                        "openrouter/free"
                );

        assertThatThrownBy(() ->
                service.update(userId, request)
        )
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("Unsupported AI model");
    }

    @Test
    void concreteProviderWithoutModel_neverSelectsFirstCatalogModel() {
        user.setAiMode(AiMode.SPECIFIC);
        user.setAiProvider("openrouter");
        user.setAiModel(null);

        assertThatThrownBy(() ->
                service.resolveForUser(userId)
        )
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("model");
    }

    @Test
    void autoIgnoresAndClearsStaleProviderAndModel() {
        user.setAiMode(AiMode.SPECIFIC);
        user.setAiProvider("openrouter");
        user.setAiModel("google/gemini-2.5-flash");

        service.update(
                userId,
                new AiPreferenceUpdateRequest(
                        AiMode.AUTO,
                        "openrouter",
                        "google/gemini-2.5-flash"
                )
        );

        assertThat(user.getAiMode())
                .isEqualTo(AiMode.AUTO);

        assertThat(user.getAiProvider())
                .isNull();

        assertThat(user.getAiModel())
                .isNull();
    }

    @Test
    void openRouterGeminiModel_resolvesExactly() {
        service.update(
                userId,
                new AiPreferenceUpdateRequest(
                        AiMode.SPECIFIC,
                        "openrouter",
                        "google/gemini-2.5-flash"
                )
        );

        AiPreferenceService.ResolvedPreference result =
                service.resolveForUser(userId);

        assertThat(result.provider())
                .isEqualTo("openrouter");

        assertThat(result.model())
                .isEqualTo("google/gemini-2.5-flash");
    }
}