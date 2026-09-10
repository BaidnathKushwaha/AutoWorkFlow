package com.autoworkflow.common.llm.openrouter;

import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.common.llm.ChatRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterModelCapabilityTest {

    private static final List<String> STRUCTURED_MODELS = List.of(
            "nvidia/nemotron-3-super-120b-a12b:free",
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free"
    );

    private static final List<String> TOOL_ONLY_MODELS = List.of(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "cohere/north-mini-code:free"
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private String capturedBody;

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

    @Test
    void registryDeclaresRequiredCapabilities() {
        for (String model : STRUCTURED_MODELS) {
            assertThat(OpenRouterModelCapabilities.forModel(model).supportsStructuredOutput()).isTrue();
            assertThat(OpenRouterModelCapabilities.forModel(model).supportsToolCalling()).isTrue();
        }
        for (String model : TOOL_ONLY_MODELS) {
            assertThat(OpenRouterModelCapabilities.forModel(model).supportsStructuredOutput()).isFalse();
            assertThat(OpenRouterModelCapabilities.forModel(model).supportsToolCalling()).isTrue();
        }
    }

    @Test
    void structuredOutputSendsResponseFormatForAllStructuredModels() throws Exception {
        for (String model : STRUCTURED_MODELS) {
            call(model);
            JsonNode body = mapper.readTree(capturedBody);
            assertThat(body.get("model").asText()).isEqualTo(model);
            assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
        }
    }

    @Test
    void structuredOutputOmitsResponseFormatForToolOnlyModels() throws Exception {
        for (String model : TOOL_ONLY_MODELS) {
            call(model);
            JsonNode body = mapper.readTree(capturedBody);
            assertThat(body.get("model").asText()).isEqualTo(model);
            assertThat(body.has("response_format")).isFalse();
        }
    }

    private void call(String model) {
        OpenRouterClient client = new OpenRouterClient(
                WebClient.builder().baseUrl(baseUrl).build());
        ReflectionTestUtils.setField(client, "platformApiKey", "test-key");
        ReflectionTestUtils.setField(client, "defaultModel", model);

        client.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("return JSON")))
                .model(model)
                .structuredOutput(true)
                .build());
    }

    private void handle(HttpExchange exchange) throws IOException {
        capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
