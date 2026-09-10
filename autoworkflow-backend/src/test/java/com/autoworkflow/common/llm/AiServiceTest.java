package com.autoworkflow.common.llm;

import com.autoworkflow.common.llm.openrouter.OpenRouterClient;
import com.autoworkflow.user.AiMode;
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
import static org.mockito.Mockito.*;

/**
 * Verifies AiProvider registration/discovery ONLY. As of Phase 8, the key -> AiProvider
 * map moved from AiService itself into AiProviderRegistry (so AiProviderRouter's AUTO
 * mode can share the exact same source of truth) — AiService now takes
 * (AiProviderRegistry, AiProviderRouter) instead of building its own map from a raw
 * List<AiProvider>, but its public contract (chat(String, ChatRequest)) is
 * unchanged. A new @Component implementing AiProvider (like OpenRouterClient) is still
 * picked up automatically with zero AiService changes required — this suite proves
 * that end-to-end for the real OpenRouterClient class, not a stand-in fake.
 */
class AiServiceTest {

    private static final String OPENROUTER_MODEL = "nvidia/nemotron-3-super-120b-a12b:free";
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
        ReflectionTestUtils.setField(client, "defaultModel", OPENROUTER_MODEL);
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
        assertThat(realOpenRouterClient().key()).isEqualTo("openrouter");
    }

    @Test
    void chatWithOpenrouter_dispatchesToTheRealOpenRouterClient_notAFakeOrAnotherProvider() {
        AiService service = serviceWithAllThreeProviders();
        ChatResponse response = service.chat("openrouter", minimalRequest());
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
        AiService service = serviceWithAllThreeProviders();
        assertThatThrownBy(() -> service.chat("does_not_exist", minimalRequest()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("openai")
                .hasMessageContaining("gemini")
                .hasMessageContaining("openrouter");
    }

    @Test
    void blankProvider_stillFallsBackToConfiguredDefault_withOpenRouterAlsoRegistered() {
        AiService service = serviceWithAllThreeProviders();
        assertThat(service.chat("", minimalRequest()).content()).isEqualTo("response from fake gemini");
        assertThat(service.chat(null, minimalRequest()).content()).isEqualTo("response from fake gemini");
    }

    @Test
    void openRouterClientAlone_registersCorrectly_evenWithoutOpenAiOrGeminiPresent() {
        AiProviderRegistry registry = new AiProviderRegistry(List.of(realOpenRouterClient()));
        AiService service = new AiService(registry, new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class)),
                fakeAiPreferenceService());
        assertThat(service.chat("openrouter", minimalRequest()).content()).isEqualTo("response from real OpenRouterClient");
    }

    private User userWithPreference(AiMode mode, String provider, String model) {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Preference User")
                .email("pref-user@example.com")
                .aiMode(mode)
                .aiProvider(provider)
                .aiModel(model)
                .build();
    }

    @Test
    void defaultProvider_withAutoPreference_routesThroughAiProviderRouter_notConfiguredDefault() {
        AiProviderRegistry registry = new AiProviderRegistry(
                List.of(fakeProvider("openai"), fakeProvider("gemini"), realOpenRouterClient()));
        AiProviderRouter router = new AiProviderRouter(registry, new AiAutoModeProperties(),
                org.mockito.Mockito.mock(com.autoworkflow.integration.IntegrationService.class));
        UUID userId = UUID.randomUUID();
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPreference(AiMode.AUTO, null, null)));
        AiService service = new AiService(registry, router, new AiPreferenceService(userRepository));
        ReflectionTestUtils.setField(service, "defaultProvider", "openai");
        ChatResponse response = service.chat("default",
                ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).userId(userId).build());
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
                .thenReturn(Optional.of(userWithPreference(AiMode.SPECIFIC, "gemini", "gemini-3.6-flash")));
        AiService service = new AiService(registry, router, new AiPreferenceService(userRepository));
        ChatResponse response = service.chat("default",
                ChatRequest.builder().messages(List.of(ChatMessage.user("hi"))).userId(userId).build());
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

    @Test
    void specificPreference_doesNotUseRouterFallback() {
        AiProvider openrouter = mock(AiProvider.class);
        when(openrouter.key()).thenReturn("openrouter");
        when(openrouter.chat(any())).thenReturn(new ChatResponse("specific-response", OPENROUTER_MODEL));

        AiProvider gemini = mock(AiProvider.class);
        when(gemini.key()).thenReturn("gemini");

        AiProviderRegistry registry = new AiProviderRegistry(List.of(openrouter, gemini));
        AiProviderRouter router = mock(AiProviderRouter.class);
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .name("Specific User")
                .email("specific@example.com")
                .aiMode(AiMode.SPECIFIC)
                .aiProvider("openrouter")
                .aiModel(OPENROUTER_MODEL)
                .build();

        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        AiPreferenceService preferenceService = new AiPreferenceService(repository);
        AiService service = new AiService(registry, router, preferenceService);

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .userId(userId)
                .build();

        ChatResponse response = service.chat("default", request);
        assertThat(response.content()).isEqualTo("specific-response");
        verify(openrouter).chat(argThat(r -> OPENROUTER_MODEL.equals(r.model())));
        verifyNoInteractions(router);
    }
}
