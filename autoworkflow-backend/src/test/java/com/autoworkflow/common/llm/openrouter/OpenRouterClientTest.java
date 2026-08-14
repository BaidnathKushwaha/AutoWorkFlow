package com.autoworkflow.common.llm.openrouter;

import com.autoworkflow.common.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises OpenRouterClient against a real local HTTP server (JDK's built-in
 * com.sun.net.httpserver, no extra test dependency) instead of mocking WebClient's
 * fluent chain — this verifies the actual wire format (headers, JSON body shape),
 * which is the part of this class most likely to have a real bug.
 */
class OpenRouterClientTest {

    private HttpServer server;
    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Captured by the fake server for assertions: last request's body and headers. */
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthHeader = new AtomicReference<>();

    /** What the fake server responds with — set per-test before calling the client. */
    private int responseStatus = 200;
    private String responseBody = "{\"choices\":[{\"message\":{\"content\":\"Hello from OpenRouter\"}}]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private OpenRouterClient client(String platformApiKey, String defaultModel) {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        OpenRouterClient client = new OpenRouterClient(webClient);
        ReflectionTestUtils.setField(client, "platformApiKey", platformApiKey);
        ReflectionTestUtils.setField(client, "defaultModel", defaultModel);
        return client;
    }

    private ChatRequest requestWith(String userApiKey, String model, Double temperature, Integer maxTokens) {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hi")))
                .userApiKey(userApiKey)
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    @Test
    void keyIsExactlyOpenrouter() {
        assertThat(client("k", "m").key()).isEqualTo("openrouter");
    }

    @Test
    void successfulChat_returnsContentAndModel() {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        ChatResponse response = client.chat(requestWith(null, "openai/gpt-4o", null, null));

        assertThat(response.content()).isEqualTo("Hello from OpenRouter");
        assertThat(response.model()).isEqualTo("openai/gpt-4o");
    }

    @Test
    void defaultModelUsed_whenRequestModelIsBlank() throws Exception {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        ChatResponse response = client.chat(requestWith(null, null, null, null));

        assertThat(response.model()).isEqualTo("openai/gpt-4o-mini");
        JsonNode sentBody = mapper.readTree(capturedBody.get());
        assertThat(sentBody.get("model").asText()).isEqualTo("openai/gpt-4o-mini");
    }

    @Test
    void requestBody_omitsTemperatureAndMaxTokens_whenNotProvided() throws Exception {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        client.chat(requestWith(null, "openai/gpt-4o", null, null));

        JsonNode sentBody = mapper.readTree(capturedBody.get());
        assertThat(sentBody.has("temperature")).isFalse();
        assertThat(sentBody.has("max_tokens")).isFalse();
        assertThat(sentBody.has("model")).isTrue();
        assertThat(sentBody.has("messages")).isTrue();
    }

    @Test
    void requestBody_includesTemperatureAndMaxTokens_whenProvided() throws Exception {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        client.chat(requestWith(null, "openai/gpt-4o", 0.3, 500));

        JsonNode sentBody = mapper.readTree(capturedBody.get());
        assertThat(sentBody.get("temperature").asDouble()).isEqualTo(0.3);
        assertThat(sentBody.get("max_tokens").asInt()).isEqualTo(500);
    }

    @Test
    void userApiKey_takesPriorityOverPlatformKey() {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        client.chat(requestWith("user-connected-key", "openai/gpt-4o", null, null));

        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer user-connected-key");
    }

    @Test
    void fallsBackToPlatformKey_whenNoUserKeyProvided() {
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");
        client.chat(requestWith(null, "openai/gpt-4o", null, null));

        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer platform-key");
    }

    @Test
    void missingApiKey_throwsClearAiException_withoutHittingNetwork() {
        OpenRouterClient client = client("", "openai/gpt-4o-mini"); // no platform key, no user key
        assertThatThrownBy(() -> client.chat(requestWith(null, "openai/gpt-4o", null, null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("OpenRouter")
                .hasMessageContaining("Integrations");
        assertThat(capturedBody.get()).isNull(); // never called the server at all
    }

    @Test
    void quotaExceeded_mapsToAiProviderExceptionWithCleanMessage() {
        responseStatus = 429;
        responseBody = "{\"error\":{\"message\":\"Rate limit exceeded, quota exhausted\"}}";
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");

        assertThatThrownBy(() -> client.chat(requestWith(null, "openai/gpt-4o", null, null)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException apiEx = (AiProviderException) ex;
                    assertThat(apiEx.getProvider()).isEqualTo("openrouter");
                    assertThat(apiEx.getHttpStatus()).isEqualTo(429);
                    assertThat(apiEx.getCode()).isEqualTo("QUOTA_EXCEEDED");
                    assertThat(apiEx.getMessage()).contains("Openrouter").contains("quota");
                });
    }

    @Test
    void authFailure_mapsToAiProviderExceptionAuthFailed() {
        responseStatus = 401;
        responseBody = "{\"error\":{\"message\":\"Invalid API key\"}}";
        OpenRouterClient client = client("bad-key", "openai/gpt-4o-mini");

        assertThatThrownBy(() -> client.chat(requestWith(null, "openai/gpt-4o", null, null)))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> assertThat(((AiProviderException) ex).getCode()).isEqualTo("AUTH_FAILED"));
    }

    @Test
    void emptyChoices_throwsAiException() {
        responseBody = "{\"choices\":[]}";
        OpenRouterClient client = client("platform-key", "openai/gpt-4o-mini");

        assertThatThrownBy(() -> client.chat(requestWith(null, "openai/gpt-4o", null, null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("empty response");
    }
}
