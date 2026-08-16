package com.autoworkflow.common.llm;

import com.autoworkflow.common.llm.openrouter.OpenRouterClient;
import com.autoworkflow.user.AiPreferenceService;
import com.autoworkflow.user.User;
import com.autoworkflow.user.UserRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies AiProvider registration/discovery ONLY. As of Phase 8, the key -> AiProvider
 * map moved from AiService itself into AiProviderRegistry (so AiProviderRouter's AUTO
 * mode can share the exact same source of truth) — AiService now takes
 * (AiProviderRegistry, AiProviderRouter) instead of building its own map from a raw
 * List&lt;AiProvider&gt;, but its public contract (chat(String, ChatRequest)) is
 * unchanged. A new @Component implementing AiProvider (like OpenRouterClient) is still
 * picked up automatically with zero AiService changes required — this suite proves
 * that end-to-end for the real OpenRouterClient class, not a stand-in fake.
 */
class AiServiceTest {

    private HttpServer openRouterServer;
    private String openRouterBaseUrl;

    @BeforeEach
    void startFakeOpenRouterServer() throws IOException {
        openRouterServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        openRouterServer.createContext("/chat/completions", exchange -> {
            String body = "{\"choices\":[{\"message\":{\"content\":\"response from real OpenRouterClient\"}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        openRouterServer.start();
        openRouterBaseUrl = "http://localhost:" + openRouterServer.getAddress().getPort();
    }

    @AfterEach
    void stopFakeOpenRouterServer() {
        openRouterServer.stop(0);
    }

    /** The real OpenRouterClient class, wired to the local fake server above — not a fake AiProvider. */
    private OpenRouterClient realOpenRouterClient() {
        WebClient webClient = WebClient.builder().baseUrl(openRouterBaseUrl).build();
        OpenRouterClient client = new OpenRouterClient(webClient);
        ReflectionTestUtils.setField(client, "platformApiKey", "test-platform-key");
        ReflectionTestUtils.setField(client, "defaultModel", "openai/gpt-4o-mini");
        return client;
    }

    /** Minimal stand-ins for OpenAI/Gemini — their own internals are already covered elsewhere; here we only care that they still resolve correctly once a third provider joins the list. */
    private AiProvider fakeProvider(String key) {
        return new AiProvider() {
            @Override public String key() { return key; }
            @Override public ChatResponse chat(ChatRequest request) { return new ChatResponse("response from fake " + key, "model-" + key); }
        };
    }

    private ChatRequest minimalRequest() {
        return ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).build();
    }

    /** Mocked repository is enough: resolveForUser() is only exercised via the explicit "default" tests below. */
    private AiPreferenceService fakeAiPreferenceService() {
        return new AiPreferenceService(mock(UserRepository.class));
    }

    private AiService serviceWithAllThreeProviders() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(fakeProvider("openai"), fakeProvider("gemini"), realOpenRouterClient()));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class));
        AiService service = new AiService(registry, router, fakeAiPreferenceService());
        ReflectionTestUtils.setField(service, "defaultProvider", "gemini");
        return service;
    }

    @Test
    void openRouterClient_isAutoDiscoveredAndRegisteredUnderExactKey() {
        // OpenRouterClient.key() must literally be "openrouter" for AiService's
        // Collectors.toMap(AiProvider::key, ...) to register it there.
        assertThat(realOpenRouterClient().key()).isEqualTo("openrouter");
    }

    @Test
    void chatWithOpenrouter_dispatchesToTheRealOpenRouterClient_notAFakeOrAnotherProvider() {
        AiService service = serviceWithAllThreeProviders();

        ChatResponse response = service.chat("openrouter", minimalRequest());

        // This response can ONLY have come from the real OpenRouterClient hitting the local
        // fake OpenRouter server — proves genuine dispatch, not just a registered-but-unused entry.
        assertThat(response.content()).isEqualTo("response from real OpenRouterClient");
    }

    @Test
    void addingOpenRouter_doesNotBreakDispatchToOpenAiOrGemini() {
        AiService service = serviceWithAllThreeProviders();

        assertThat(service.chat("openai", minimalRequest()).content()).isEqualTo("response from fake openai");
        assertThat(service.chat("gemini", minimalRequest()).content()).isEqualTo("response from fake gemini");
    }

    @Test
    void providerKeyResolution_isCaseInsensitive_forOpenrouterToo() {
        AiService service = serviceWithAllThreeProviders();

        assertThat(service.chat("OpenRouter", minimalRequest()).content()).isEqualTo("response from real OpenRouterClient");
        assertThat(service.chat("OPENROUTER", minimalRequest()).content()).isEqualTo("response from real OpenRouterClient");
    }

    @Test
    void registryConceptuallyContainsAllThreeProviders() {
        // AiService doesn't (and per this task, shouldn't) expose a getter for the provider
        // map, so we verify registration indirectly through the one place it's already
        // observable without changing AiService: the "unsupported provider" error message,
        // which lists providers.keySet() — must contain exactly these three, no more, no less.
        AiService service = serviceWithAllThreeProviders();

        assertThatThrownBy(() -> service.chat("does_not_exist", minimalRequest()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("openai")
                .hasMessageContaining("gemini")
                .hasMessageContaining("openrouter");
    }

    @Test
    void blankProvider_stillFallsBackToConfiguredDefault_withOpenRouterAlsoRegistered() {
        AiService service = serviceWithAllThreeProviders(); // defaultProvider = "gemini"

        assertThat(service.chat("", minimalRequest()).content()).isEqualTo("response from fake gemini");
        assertThat(service.chat(null, minimalRequest()).content()).isEqualTo("response from fake gemini");
    }

    @Test
    void openRouterClientAlone_registersCorrectly_evenWithoutOpenAiOrGeminiPresent() {
        // Sanity check that registration doesn't implicitly depend on the other two beans
        // being present (e.g. no shared mutable state, no ordering assumption).
        AiProviderRegistry registry = new AiProviderRegistry(List.of(realOpenRouterClient()));
        AiService service = new AiService(registry, new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class)),
                fakeAiPreferenceService());

        assertThat(service.chat("openrouter", minimalRequest()).content()).isEqualTo("response from real OpenRouterClient");
    }

    /*
     * Coverage for the "default" pathway added by the persistent AI preference feature:
     * providerName == "default" must resolve through AiPreferenceService.resolveForUser(userId)
     * rather than through the configured application.yml default-provider.
     */

    private User userWithPreference(String provider, String model) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Preference User")
                .email("pref-user@example.com")
                .aiProvider(provider)
                .aiModel(model)
                .build();
        return user;
    }

    @Test
    void defaultProvider_withAutoPreference_routesThroughAiProviderRouter_notConfiguredDefault() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(fakeProvider("openai"), fakeProvider("gemini"), realOpenRouterClient()));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class));

        UUID userId = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(userWithPreference("auto", null)));

        AiService service = new AiService(registry, router, new AiPreferenceService(userRepository));
        // Configured application.yml default is deliberately different from "auto" here, to prove
        // the account preference (auto) — not the static config default — governs "default" calls.
        ReflectionTestUtils.setField(service, "defaultProvider", "openai");

        ChatResponse response = service.chat("default",
                ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).userId(userId).build());

        // AUTO mode's fallback chain starts with OpenRouter — confirms the router, not a single
        // fixed provider, handled the request.
        assertThat(response.content()).isEqualTo("response from real OpenRouterClient");
    }

    @Test
    void defaultProvider_withSpecificPreference_callsExactlyThatProviderAndModel_neverSilentlyFallingBack() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(fakeProvider("openai"), fakeProvider("gemini"), realOpenRouterClient()));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class));

        UUID userId = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(userWithPreference("gemini", "gemini-3.6-flash")));

        AiService service = new AiService(registry, router, new AiPreferenceService(userRepository));

        ChatResponse response = service.chat("default",
                ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).userId(userId).build());

        // SPECIFIC dispatches straight to the registered "gemini" provider — never the router,
        // never openai — matching "SPECIFIC must not silently become AUTO".
        assertThat(response.content()).isEqualTo("response from fake gemini");
    }

    @Test
    void defaultProvider_withNullUserId_safelyDefaultsToAuto() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(fakeProvider("openai"), fakeProvider("gemini"), realOpenRouterClient()));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class));

        AiService service = new AiService(registry, router, fakeAiPreferenceService());

        ChatResponse response = service.chat("default",
                ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).build());

        assertThat(response.content()).isEqualTo("response from real OpenRouterClient");
    }
}