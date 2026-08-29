package com.autoworkflow.common.llm;

import com.autoworkflow.integration.IntegrationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiProviderRouterPhase1AcceptanceTest {

    private AiProvider provider(
            String key,
            java.util.function.Function<ChatRequest, ChatResponse> behavior
    ) {
        return new AiProvider() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return behavior.apply(request);
            }
        };
    }

    private AiProviderRouter router(
            List<AiProvider> providers,
            List<String> order
    ) {
        AiProviderRegistry registry = new AiProviderRegistry(providers);
        AiAutoModeProperties properties = new AiAutoModeProperties();
        properties.setAutoProviderOrder(order);
        return new AiProviderRouter(
                registry,
                properties,
                mock(IntegrationService.class)
        );
    }

    private ChatRequest request() {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .userId(UUID.randomUUID())
                .build();
    }

    @Test
    void openRouter402_quotaFailureFallsThroughToGemini() {
        AiProvider openrouter = provider(
                "openrouter",
                ignored -> {
                    throw AiProviderException.from(
                            "openrouter",
                            402,
                            "{\"error\":\"quota exhausted\"}"
                    );
                }
        );

        AiProvider gemini = provider(
                "gemini",
                ignored -> new ChatResponse("gemini response", "gemini-default")
        );

        ChatResponse response = router(
                List.of(openrouter, gemini),
                List.of("openrouter", "gemini")
        ).chat(request());

        assertThat(response.content()).isEqualTo("gemini response");
        assertThat(response.actualProvider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.attemptedProviders())
                .containsExactly("openrouter", "gemini");
    }

    @Test
    void autoMode_preservesStructuredOutputWhenStrippingModel() {
        AtomicReference<ChatRequest> received = new AtomicReference<>();

        AiProvider gemini = provider(
                "gemini",
                request -> {
                    received.set(request);
                    return new ChatResponse("ok", "gemini-default");
                }
        );

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .model("provider-specific-model")
                .structuredOutput(true)
                .build();

        router(List.of(gemini), List.of("gemini")).chat(request);

        assertThat(received.get().model()).isNull();
        assertThat(received.get().structuredOutput()).isTrue();
    }

    @Test
    void nonRetryableFailure_stopsBeforeNextProvider() {
        AiProvider openrouter = provider(
                "openrouter",
                ignored -> {
                    throw AiProviderException.from(
                            "openrouter",
                            401,
                            "{\"error\":\"invalid_api_key\"}"
                    );
                }
        );

        AiProvider gemini = provider(
                "gemini",
                ignored -> new ChatResponse("must not run", "gemini-default")
        );

        assertThatThrownBy(() -> router(
                List.of(openrouter, gemini),
                List.of("openrouter", "gemini")
        ).chat(request()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(error -> assertThat(((AiProviderException) error).getCode())
                        .isEqualTo("AUTH_FAILED"));
    }
}
