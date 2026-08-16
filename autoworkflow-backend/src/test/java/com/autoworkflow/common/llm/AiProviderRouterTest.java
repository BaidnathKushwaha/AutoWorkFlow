package com.autoworkflow.common.llm;

import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.integration.IntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiProviderRouterTest {

    private IntegrationService integrationService;
    private final List<String> callOrder = new ArrayList<>();

    @BeforeEach
    void setUp() {
        integrationService = mock(IntegrationService.class);
        callOrder.clear();
    }

    private AiProvider fakeProvider(
            String key,
            Function<ChatRequest, ChatResponse> behavior
    ) {
        return new AiProvider() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                callOrder.add(key);
                return behavior.apply(request);
            }
        };
    }

    private AiProvider succeeding(
            String key,
            String content
    ) {
        return fakeProvider(
                key,
                request ->
                        new ChatResponse(
                                content,
                                key + "-default-model"
                        )
        );
    }

    private AiProvider throwing(
            String key,
            RuntimeException exception
    ) {
        return fakeProvider(
                key,
                request -> {
                    throw exception;
                }
        );
    }

    private AiProviderRouter router(
            AiProviderRegistry registry,
            List<String> order
    ) {
        AiAutoModeProperties properties =
                new AiAutoModeProperties();

        properties.setAutoProviderOrder(order);

        return new AiProviderRouter(
                registry,
                properties,
                integrationService
        );
    }

    private ChatRequest requestFor(UUID userId) {
        return ChatRequest.builder()
                .messages(
                        List.of(
                                ChatMessage.user("hi")
                        )
                )
                .userId(userId)
                .build();
    }

    /*
     * 1. OpenRouter success -> no fallback
     */
    @Test
    void openRouterSuccess_doesNotFallback() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                succeeding(
                                        "openrouter",
                                        "from openrouter"
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.content())
                .isEqualTo("from openrouter");

        assertThat(response.actualProvider())
                .isEqualTo("openrouter");

        assertThat(response.fallbackUsed())
                .isFalse();

        assertThat(response.attemptedProviders())
                .containsExactly("openrouter");

        assertThat(callOrder)
                .containsExactly("openrouter");
    }

    /*
     * 2. OpenRouter quota -> Gemini
     */
    @Test
    void openRouterQuotaExceeded_fallsBackToGemini() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                429,
                                                "{\"error\":\"quota\"}"
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("gemini");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(response.attemptedProviders())
                .containsExactly(
                        "openrouter",
                        "gemini"
                );

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini"
                );
    }

    /*
     * 3. OpenRouter 5xx -> Gemini
     */
    @Test
    void openRouterFiveHundredError_fallsBackToGemini() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                503,
                                                "{\"error\":\"unavailable\"}"
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("gemini");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(response.attemptedProviders())
                .containsExactly(
                        "openrouter",
                        "gemini"
                );

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini"
                );
    }

    /*
     * 4. Gemini failure -> OpenAI
     */
    @Test
    void geminiFailure_fallsBackToOpenAI() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        new NoCredentialsException(
                                                "No OpenRouter API key configured."
                                        )
                                ),
                                throwing(
                                        "gemini",
                                        AiProviderException.from(
                                                "gemini",
                                                503,
                                                "{\"error\":\"unavailable\"}"
                                        )
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("openai");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(response.attemptedProviders())
                .containsExactly(
                        "openrouter",
                        "gemini",
                        "openai"
                );

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini",
                        "openai"
                );
    }

    /*
     * 5. OpenRouter + Gemini failure -> OpenAI
     */
    @Test
    void openRouterAndGeminiFailure_fallsBackToOpenAI() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                503,
                                                "{\"error\":\"unavailable\"}"
                                        )
                                ),
                                throwing(
                                        "gemini",
                                        new AiException(
                                                "Gemini request failed: timeout"
                                        )
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("openai");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(response.attemptedProviders())
                .containsExactly(
                        "openrouter",
                        "gemini",
                        "openai"
                );

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini",
                        "openai"
                );
    }

    /*
     * 6. All providers fail -> controlled failure
     */
    @Test
    void allProvidersFail_returnsControlledFailure() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                503,
                                                "{}"
                                        )
                                ),
                                throwing(
                                        "gemini",
                                        new NoCredentialsException(
                                                "No Gemini API key configured."
                                        )
                                ),
                                throwing(
                                        "openai",
                                        AiProviderException.from(
                                                "openai",
                                                503,
                                                "{}"
                                        )
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        assertThatThrownBy(
                () -> router.chat(requestFor(null))
        )
                .isInstanceOf(AiException.class)
                .hasMessageContaining(
                        "All configured AI providers failed"
                )
                .hasMessageContaining("openrouter")
                .hasMessageContaining("gemini")
                .hasMessageContaining("openai");

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini",
                        "openai"
                );
    }

    /*
     * 7. AUTH_FAILED -> no fallback
     */
    @Test
    void authFailed_doesNotFallback() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                401,
                                                "{\"error\":\"invalid_api_key\"}"
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        assertThatThrownBy(
                () -> router.chat(requestFor(null))
        )
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException =
                            (AiProviderException) exception;

                    assertThat(providerException.getCode())
                            .isEqualTo("AUTH_FAILED");
                });

        assertThat(callOrder)
                .containsExactly("openrouter");
    }

    /*
     * 8. INVALID_MODEL -> no fallback
     */
    @Test
    void invalidModel_doesNotFallback() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        AiProviderException.from(
                                                "openrouter",
                                                400,
                                                "{\"error\":\"model not_found\"}"
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                ),
                                succeeding(
                                        "openai",
                                        "from openai"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini",
                                "openai"
                        )
                );

        assertThatThrownBy(
                () -> router.chat(requestFor(null))
        )
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException =
                            (AiProviderException) exception;

                    assertThat(providerException.getCode())
                            .isEqualTo("INVALID_MODEL");
                });

        assertThat(callOrder)
                .containsExactly("openrouter");
    }

    /*
     * Existing timeout / connection failure behavior remains retryable.
     */
    @Test
    void networkFailure_fallsBackToNextProvider() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        new AiException(
                                                "OpenRouter request failed: timeout"
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("gemini");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini"
                );
    }

    /*
     * No credentials is different from rejected credentials.
     * No credentials is retryable.
     */
    @Test
    void noCredentials_fallsBackToNextProvider() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                throwing(
                                        "openrouter",
                                        new NoCredentialsException(
                                                "No OpenRouter API key configured."
                                        )
                                ),
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("gemini");

        assertThat(response.fallbackUsed())
                .isTrue();

        assertThat(callOrder)
                .containsExactly(
                        "openrouter",
                        "gemini"
                );
    }

    /*
     * Explicit provider selection must never use AUTO fallback.
     */
    @Test
    void explicitProviderSelection_doesNotFallback() {
        AiProvider openAi =
                throwing(
                        "openai",
                        AiProviderException.from(
                                "openai",
                                429,
                                "{}"
                        )
                );

        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                openAi,
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                )
                        )
                );

        AiAutoModeProperties properties =
                new AiAutoModeProperties();

        AiProviderRouter router =
                new AiProviderRouter(
                        registry,
                        properties,
                        integrationService
                );

        AiService service =
                new AiService(
                        registry,
                        router,
                        new com.autoworkflow.user.AiPreferenceService(
                                mock(com.autoworkflow.user.UserRepository.class)
                        )
                );

        assertThatThrownBy(
                () ->
                        service.chat(
                                "openai",
                                requestFor(null)
                        )
        )
                .isInstanceOf(AiProviderException.class);

        assertThat(callOrder)
                .containsExactly("openai");
    }

    /*
     * Unregistered provider names in configuration are skipped.
     */
    @Test
    void unregisteredProvider_isSkipped() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                succeeding(
                                        "gemini",
                                        "from gemini"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini"
                        )
                );

        ChatResponse response =
                router.chat(requestFor(null));

        assertThat(response.actualProvider())
                .isEqualTo("gemini");

        assertThat(response.attemptedProviders())
                .containsExactly("gemini");

        assertThat(callOrder)
                .containsExactly("gemini");
    }

    /*
     * Model must be stripped in AUTO mode so a provider-specific model
     * is never accidentally sent to another provider.
     */
    @Test
    void autoMode_stripsConfiguredModel() {
        AtomicInteger modelWasNull =
                new AtomicInteger();

        AiProvider recordingProvider =
                new AiProvider() {

                    @Override
                    public String key() {
                        return "gemini";
                    }

                    @Override
                    public ChatResponse chat(
                            ChatRequest request
                    ) {
                        if (request.model() == null) {
                            modelWasNull.incrementAndGet();
                        }

                        return new ChatResponse(
                                "ok",
                                "gemini-default"
                        );
                    }
                };

        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(recordingProvider)
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of("gemini")
                );

        ChatRequest request =
                ChatRequest.builder()
                        .messages(
                                List.of(
                                        ChatMessage.user("hi")
                                )
                        )
                        .model("gpt-4o")
                        .build();

        router.chat(request);

        assertThat(modelWasNull.get())
                .isEqualTo(1);
    }

    /*
     * Each fallback attempt resolves credentials for the actual provider.
     */
    @Test
    void autoMode_resolvesFreshUserKeyPerProvider() {
        UUID userId =
                UUID.randomUUID();

        when(
                integrationService
                        .getDecryptedAccessToken(
                                userId,
                                "openrouter"
                        )
        )
                .thenThrow(
                        new ResourceNotFoundException(
                                "not connected"
                        )
                );

        when(
                integrationService
                        .getDecryptedAccessToken(
                                userId,
                                "gemini"
                        )
        )
                .thenReturn(
                        "users-gemini-key"
                );

        AtomicInteger geminiSawUserKey =
                new AtomicInteger();

        AiProvider openRouter =
                throwing(
                        "openrouter",
                        new NoCredentialsException(
                                "no key"
                        )
                );

        AiProvider gemini =
                new AiProvider() {

                    @Override
                    public String key() {
                        return "gemini";
                    }

                    @Override
                    public ChatResponse chat(
                            ChatRequest request
                    ) {
                        if (
                                "users-gemini-key"
                                        .equals(
                                                request.userApiKey()
                                        )
                        ) {
                            geminiSawUserKey.incrementAndGet();
                        }

                        return new ChatResponse(
                                "ok",
                                "gemini-default"
                        );
                    }
                };

        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                openRouter,
                                gemini
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of(
                                "openrouter",
                                "gemini"
                        )
                );

        router.chat(
                requestFor(userId)
        );

        assertThat(
                geminiSawUserKey.get()
        )
                .isEqualTo(1);
    }

    @Test
    void autoMode_withoutUserId_doesNotCallIntegrationService() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                succeeding(
                                        "gemini",
                                        "ok"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of("gemini")
                );

        router.chat(requestFor(null));

        verifyNoInteractions(
                integrationService
        );
    }

    @Test
    void unexpectedIntegrationServiceFailure_propagates() {
        UUID userId =
                UUID.randomUUID();

        when(
                integrationService
                        .getDecryptedAccessToken(
                                userId,
                                "gemini"
                        )
        )
                .thenThrow(
                        new IllegalStateException(
                                "token decryption failed"
                        )
                );

        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                succeeding(
                                        "gemini",
                                        "ok"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of("gemini")
                );

        assertThatThrownBy(
                () ->
                        router.chat(
                                requestFor(userId)
                        )
        )
                .isInstanceOf(
                        IllegalStateException.class
                );
    }

    @Test
    void emptyProviderOrder_returnsControlledConfigurationFailure() {
        AiProviderRegistry registry =
                new AiProviderRegistry(
                        List.of(
                                succeeding(
                                        "gemini",
                                        "ok"
                                )
                        )
                );

        AiProviderRouter router =
                router(
                        registry,
                        List.of()
                );

        assertThatThrownBy(
                () ->
                        router.chat(
                                requestFor(null)
                        )
        )
                .isInstanceOf(AiException.class)
                .hasMessageContaining(
                        "auto-provider-order"
                );
    }
}