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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 8's required AUTO provider mode scenarios (section 10, 1-10 — scenarios
 * 11-14 about existing OpenAI/Gemini/OpenRouter/saved-workflow behavior being unchanged
 * are covered by AiServiceTest, AiNodeStrategyTest/SummarizerStrategyTest/etc, and code
 * inspection respectively, since "auto" is purely additive to those).
 *
 * Uses lightweight fake AiProvider implementations rather than the real clients — this
 * suite's job is to verify ROUTING/fallback decisions in isolation, deterministically;
 * the real clients' actual HTTP/error-mapping behavior is already covered separately by
 * OpenRouterClientTest.
 */
class AiProviderRouterTest {

    private IntegrationService integrationService;
    private final List<String> callOrder = new ArrayList<>();

    @BeforeEach
    void setUp() {
        integrationService = mock(IntegrationService.class);
        callOrder.clear();
    }

    /** A fake provider whose behavior (throw or succeed) is controlled per-test. */
    private AiProvider fakeProvider(String key, Function<ChatRequest, ChatResponse> behavior) {
        return new AiProvider() {
            @Override public String key() { return key; }
            @Override public ChatResponse chat(ChatRequest request) {
                callOrder.add(key);
                return behavior.apply(request);
            }
        };
    }

    private AiProvider succeeding(String key, String content) {
        return fakeProvider(key, req -> new ChatResponse(content, key + "-default-model"));
    }

    private AiProvider throwing(String key, RuntimeException exception) {
        return fakeProvider(key, req -> { throw exception; });
    }

    private AiProviderRouter router(AiProviderRegistry registry, List<String> order) {
        AiAutoModeProperties props = new AiAutoModeProperties();
        props.setAutoProviderOrder(order);
        return new AiProviderRouter(registry, props, integrationService);
    }

    private ChatRequest requestFor(UUID userId) {
        return ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).userId(userId).build();
    }

    // --- 1. AUTO chooses first available provider ---

    @Test
    void autoChoosesFirstAvailableProvider() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                succeeding("openrouter", "from openrouter"), succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini", "openai"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.content()).isEqualTo("from openrouter");
        assertThat(response.actualProvider()).isEqualTo("openrouter");
        assertThat(response.fallbackUsed()).isFalse();
        assertThat(response.attemptedProviders()).containsExactly("openrouter");
        assertThat(callOrder).containsExactly("openrouter"); // gemini/openai never even called
    }

    // --- 2/3. AUTO falls back after HTTP 429 / temporary 5xx ---

    @Test
    void autoFallsBackAfterQuotaExceeded429() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 429, "{\"error\":\"quota\"}")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini", "openai"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.actualProvider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.attemptedProviders()).containsExactly("openrouter", "gemini");
    }

    @Test
    void autoFallsBackAfterTemporary5xx() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 503, "{\"error\":\"unavailable\"}")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.actualProvider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isTrue();
    }

    // --- 4. AUTO falls back after timeout / connection failure ---

    @Test
    void autoFallsBackAfterTimeoutOrConnectionFailure() {
        // This is exactly what OpenAiClient/GeminiClient/OpenRouterClient throw for a
        // network-level failure (see their `catch (Exception e)` fallback) — a generic
        // AiException, not an AiProviderException (no HTTP response was ever received).
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", new AiException("OpenRouter request failed: timeout")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.actualProvider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isTrue();
    }

    // --- 5. AUTO does NOT fallback after invalid request / permanent errors ---

    @Test
    void autoDoesNotFallback_afterInvalidModel() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 400, "{\"error\":\"model not_found\"}")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        assertThatThrownBy(() -> router.chat(requestFor(null))).isInstanceOf(AiProviderException.class);
        assertThat(callOrder).containsExactly("openrouter"); // gemini never attempted
    }

    @Test
    void autoDoesNotFallback_afterAuthRejected_credentialsExistButAreInvalid() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 401, "{\"error\":\"invalid_api_key\"}")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        assertThatThrownBy(() -> router.chat(requestFor(null))).isInstanceOf(AiProviderException.class);
        assertThat(callOrder).containsExactly("openrouter");
    }

    @Test
    void autoDoesNotFallback_forUnexpectedExceptionTypes_failsClosed() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", new IllegalStateException("something unrelated broke")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        assertThatThrownBy(() -> router.chat(requestFor(null))).isInstanceOf(IllegalStateException.class);
        assertThat(callOrder).containsExactly("openrouter");
    }

    // --- "no credentials at all" IS retryable, distinct from "credentials rejected" ---

    @Test
    void autoTreatsNoCredentialsAsUnavailable_andContinues() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", new NoCredentialsException("No OpenRouter API key configured.")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.actualProvider()).isEqualTo("gemini");
        assertThat(response.fallbackUsed()).isTrue();
    }

    // --- 6. AUTO does NOT silently fallback when the user explicitly selected a provider ---

    @Test
    void manualProviderSelection_neverGoesThroughTheRouterAtAll() {
        AiProvider openai = throwing("openai", AiProviderException.from("openai", 429, "{}"));
        AiProviderRegistry registry = new AiProviderRegistry(List.of(openai, succeeding("gemini", "from gemini")));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(), integrationService);
        AiService service = new AiService(registry, router);

        // Explicit provider="openai" must fail exactly as it always has — no fallback to
        // gemini, even though gemini is registered and would have succeeded.
        assertThatThrownBy(() -> service.chat("openai", requestFor(null))).isInstanceOf(AiProviderException.class);
        assertThat(callOrder).containsExactly("openai"); // gemini never touched
    }

    // --- 7. AUTO skips unavailable (misconfigured / unregistered) providers ---

    @Test
    void autoSkipsProviderOrderEntriesNotActuallyRegistered() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(succeeding("gemini", "from gemini")));
        // "openrouter" is in the configured order but no such bean is registered.
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.actualProvider()).isEqualTo("gemini");
        // Not counted as an "attempt" since it was never actually callable.
        assertThat(response.attemptedProviders()).containsExactly("gemini");
    }

    // --- 8/9. AUTO reports actual provider + safe fallback metadata ---

    @Test
    void autoReportsFallbackMetadata_withoutLeakingExceptionInternals() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 429, "{\"secret_internal_field\":\"should-not-leak\"}")),
                succeeding("gemini", "from gemini")));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        ChatResponse response = router.chat(requestFor(null));

        assertThat(response.attemptedProviders()).containsExactly("openrouter", "gemini");
        assertThat(response.fallbackUsed()).isTrue();
    }

    // --- 10. All providers failing returns one meaningful error, no leaked internals ---

    @Test
    void allProvidersFailing_returnsOneMeaningfulError() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(
                throwing("openrouter", AiProviderException.from("openrouter", 429, "{}")),
                throwing("gemini", new NoCredentialsException("No Gemini API key configured.")),
                throwing("openai", AiProviderException.from("openai", 503, "{}"))));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini", "openai"));

        assertThatThrownBy(() -> router.chat(requestFor(null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("All configured AI providers failed")
                .hasMessageContaining("openrouter")
                .hasMessageContaining("gemini")
                .hasMessageContaining("openai");
        assertThat(callOrder).containsExactly("openrouter", "gemini", "openai");
    }

    @Test
    void emptyProviderOrder_failsWithClearConfigurationError() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(succeeding("gemini", "x")));
        AiProviderRouter router = router(registry, List.of());

        assertThatThrownBy(() -> router.chat(requestFor(null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("auto-provider-order");
    }

    // --- model stripping: AUTO never sends a provider-specific model to another provider ---

    @Test
    void autoModeStripsConfiguredModel_soItsNeverSentToTheWrongProvider() {
        AtomicInteger receivedModel = new AtomicInteger(-1);
        AiProvider recordingProvider = new AiProvider() {
            @Override public String key() { return "gemini"; }
            @Override public ChatResponse chat(ChatRequest request) {
                receivedModel.set(request.model() == null ? 1 : 0);
                return new ChatResponse("ok", "gemini-default");
            }
        };
        AiProviderRegistry registry = new AiProviderRegistry(List.of(recordingProvider));
        AiProviderRouter router = router(registry, List.of("gemini"));

        ChatRequest requestWithOpenAiModel = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .model("gpt-4o") // an OpenAI-specific model string
                .build();

        router.chat(requestWithOpenAiModel);

        assertThat(receivedModel.get()).isEqualTo(1); // model was null by the time gemini received it
    }

    // --- credential resolution per attempt: user key preferred, platform key otherwise ---

    @Test
    void autoResolvesAFreshUserKeyPerProviderAttempt() {
        UUID userId = UUID.randomUUID();
        when(integrationService.getDecryptedAccessToken(userId, "gemini")).thenReturn("users-gemini-key");
        when(integrationService.getDecryptedAccessToken(userId, "openrouter")).thenThrow(new ResourceNotFoundException("not connected"));

        AtomicInteger geminiSawUserKey = new AtomicInteger(0);
        AiProvider openrouter = throwing("openrouter", new NoCredentialsException("no key"));
        AiProvider gemini = new AiProvider() {
            @Override public String key() { return "gemini"; }
            @Override public ChatResponse chat(ChatRequest request) {
                if ("users-gemini-key".equals(request.userApiKey())) geminiSawUserKey.incrementAndGet();
                return new ChatResponse("ok", "gemini-default");
            }
        };
        AiProviderRegistry registry = new AiProviderRegistry(List.of(openrouter, gemini));
        AiProviderRouter router = router(registry, List.of("openrouter", "gemini"));

        router.chat(requestFor(userId));

        assertThat(geminiSawUserKey.get()).isEqualTo(1);
    }

    @Test
    void autoWithoutUserId_neverCallsIntegrationService_platformKeyOnly() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(succeeding("gemini", "ok")));
        AiProviderRouter router = router(registry, List.of("gemini"));

        router.chat(requestFor(null)); // no userId set

        verifyNoInteractions(integrationService);
    }

    @Test
    void unexpectedIntegrationServiceError_propagates_notTreatedAsNoCredentials() {
        UUID userId = UUID.randomUUID();
        when(integrationService.getDecryptedAccessToken(userId, "gemini"))
                .thenThrow(new IllegalStateException("token decryption failed"));
        AiProviderRegistry registry = new AiProviderRegistry(List.of(succeeding("gemini", "ok")));
        AiProviderRouter router = router(registry, List.of("gemini"));

        assertThatThrownBy(() -> router.chat(requestFor(userId))).isInstanceOf(IllegalStateException.class);
    }
}
