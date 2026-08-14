package com.autoworkflow.common.llm.openrouter;

import com.autoworkflow.common.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenRouter — a third AiProvider alongside OpenAiClient and GeminiClient.
 * OpenRouter
 * exposes an OpenAI-compatible Chat Completions API (POST /chat/completions,
 * Bearer
 * auth, same request/response shape), so this client mirrors OpenAiClient's
 * structure
 * rather than GeminiClient's. No changes were made to AiService, AiProvider,
 * ChatRequest/ChatResponse, WorkflowExecutor, NodeStrategyRegistry, or the
 * execution
 * strategies — this is a pure additive registration under key "openrouter".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterClient implements AiProvider {

    @Override
    public String key() {
        return "openrouter";
    }

    private final WebClient openRouterWebClient;

    @Value("${app.openrouter.api-key:}")
    private String platformApiKey;

    @Value("${app.openrouter.default-model}")
    private String defaultModel;

    @Override
    public ChatResponse chat(ChatRequest request) {
        // Same cascade as OpenAiClient/GeminiClient: per-user connected key (passed in
        // via
        // request.userApiKey() by the calling strategy, resolved through
        // IntegrationService)
        // takes priority, falling back to the platform-wide key from application
        // config.
        String apiKey = (request.userApiKey() != null && !request.userApiKey().isBlank()) ? request.userApiKey()
                : platformApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new NoCredentialsException(
                    "No OpenRouter API key configured. Connect OpenRouter in Integrations or set app.openrouter.api-key.");
        }

        String model = (request.model() != null && !request.model().isBlank()) ? request.model() : defaultModel;

        // Only include optional fields the caller actually provided — unlike
        // OpenAiClient,
        // which always sends temperature (defaulting to 0.7). OpenRouter proxies to
        // many
        // different underlying models with different accepted parameter sets, so
        // omitting
        // fields the caller didn't ask for is the safer default here.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", request.messages().stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            body.put("max_tokens", request.maxTokens());
        }

        log.debug("Calling OpenRouter model={}", model);

        try {
            JsonNode response = openRouterWebClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> reactor.core.publisher.Mono.error(
                                            AiProviderException.from("openrouter", clientResponse.statusCode().value(),
                                                    errorBody))))
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                throw new AiException("OpenRouter returned an empty response");
            }
            JsonNode message = response.get("choices")
                    .get(0)
                    .get("message");

            JsonNode contentNode = message.get("content");

            if (contentNode == null || contentNode.isNull()) {
                log.warn("OpenRouter returned null message content. Response: {}", response);
                throw new AiException("OpenRouter returned a response with no message content");
            }

            String content = contentNode.asText();

            if (content.isBlank()) {
                log.warn("OpenRouter returned blank message content. Response: {}", response);
                throw new AiException("OpenRouter returned an empty message content");
            }

            return new ChatResponse(content, model);
        } catch (AiProviderException e) {
            log.warn("OpenRouter call failed: provider={} status={} code={}", e.getProvider(), e.getHttpStatus(),
                    e.getCode());
            throw e;
        } catch (Exception e) {
            throw new AiException("OpenRouter request failed: " + e.getMessage(), e);
        }
    }
}
