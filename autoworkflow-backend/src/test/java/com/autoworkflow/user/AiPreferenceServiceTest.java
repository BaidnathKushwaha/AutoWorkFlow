package com.autoworkflow.user;

import com.autoworkflow.common.exception.InvalidAiPreferenceException;
import com.autoworkflow.user.dto.AiPreferenceUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiPreferenceServiceTest {

    private static final List<String> CURATED_OPENROUTER_MODELS = List.of(
            "nvidia/nemotron-3-super-120b-a12b:free",
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "cohere/north-mini-code:free"
    );

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
        AiPreferenceService.ResolvedPreference result = service.resolveForUser(userId);

        assertThat(result.mode()).isEqualTo(AiMode.AUTO);
        assertThat(result.provider()).isNull();
        assertThat(result.model()).isNull();
    }

    @Test
    void specificPreference_persistsAndResolvesExactSelection() {
        String selectedModel = CURATED_OPENROUTER_MODELS.get(0);
        AiPreferenceUpdateRequest request = new AiPreferenceUpdateRequest(
                AiMode.SPECIFIC,
                "openrouter",
                selectedModel
        );

        service.update(userId, request);

        assertThat(user.getAiMode()).isEqualTo(AiMode.SPECIFIC);
        assertThat(user.getAiProvider()).isEqualTo("openrouter");
        assertThat(user.getAiModel()).isEqualTo(selectedModel);

        AiPreferenceService.ResolvedPreference result = service.resolveForUser(userId);
        assertThat(result.mode()).isEqualTo(AiMode.SPECIFIC);
        assertThat(result.provider()).isEqualTo("openrouter");
        assertThat(result.model()).isEqualTo(selectedModel);
    }

    @Test
    void curatedOpenRouterModels_areExactlyFiveInRequiredOrderAndPersistExactly() {
        var response = service.get(userId);
        var openRouter = response.providers().stream()
                .filter(provider -> provider.key().equals("openrouter"))
                .findFirst()
                .orElseThrow();

        assertThat(openRouter.models()).containsExactlyElementsOf(CURATED_OPENROUTER_MODELS);
        assertThat(openRouter.models()).doesNotContain(
                "deepseek/deepseek-v4-flash:free",
                "openai/gpt-oss-120b:free",
                "qwen/qwen3-235b-a22b-2507:free",
                "google/gemini-2.5-flash",
                "openrouter/free"
        );

        for (String model : CURATED_OPENROUTER_MODELS) {
            service.update(userId, new AiPreferenceUpdateRequest(
                    AiMode.SPECIFIC, "openrouter", model));
            assertThat(service.resolveForUser(userId).model()).isEqualTo(model);
        }
    }

    @Test
    void specificWithoutProvider_isRejected() {
        AiPreferenceUpdateRequest request = new AiPreferenceUpdateRequest(
                AiMode.SPECIFIC, null, CURATED_OPENROUTER_MODELS.get(0));

        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void specificWithoutModel_isRejected() {
        AiPreferenceUpdateRequest request = new AiPreferenceUpdateRequest(
                AiMode.SPECIFIC, "openrouter", null);

        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("model");
    }

    @Test
    void unknownProvider_isRejected() {
        AiPreferenceUpdateRequest request = new AiPreferenceUpdateRequest(
                AiMode.SPECIFIC, "unknown-provider", "some-model");

        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("Unsupported AI provider");
    }

    @Test
    void unsupportedModel_isRejected() {
        AiPreferenceUpdateRequest request = new AiPreferenceUpdateRequest(
                AiMode.SPECIFIC, "openrouter", "openrouter/free");

        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("Unsupported AI model");
    }

    @Test
    void concreteProviderWithoutModel_neverSelectsFirstCatalogModel() {
        user.setAiMode(AiMode.SPECIFIC);
        user.setAiProvider("openrouter");
        user.setAiModel(null);

        assertThatThrownBy(() -> service.resolveForUser(userId))
                .isInstanceOf(InvalidAiPreferenceException.class)
                .hasMessageContaining("model");
    }

    @Test
    void autoIgnoresAndClearsStaleProviderAndModel() {
        user.setAiMode(AiMode.SPECIFIC);
        user.setAiProvider("openrouter");
        user.setAiModel(CURATED_OPENROUTER_MODELS.get(0));

        service.update(userId, new AiPreferenceUpdateRequest(
                AiMode.AUTO, "openrouter", CURATED_OPENROUTER_MODELS.get(0)));

        assertThat(user.getAiMode()).isEqualTo(AiMode.AUTO);
        assertThat(user.getAiProvider()).isNull();
        assertThat(user.getAiModel()).isNull();
    }

    @Test
    void specificPersistence_isExactForStructuredOutputModels() {
        for (String model : CURATED_OPENROUTER_MODELS.subList(0, 3)) {
            service.update(userId, new AiPreferenceUpdateRequest(
                    AiMode.SPECIFIC, "openrouter", model));

            assertThat(user.getAiModel()).isEqualTo(model);
            assertThat(service.resolveForUser(userId).model()).isEqualTo(model);
        }
    }
}
