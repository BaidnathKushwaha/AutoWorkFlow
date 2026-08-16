package com.autoworkflow.common.llm.openrouter;

import com.autoworkflow.common.llm.AiException;
import com.autoworkflow.common.llm.AiProvider;
import com.autoworkflow.common.llm.AiProviderException;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.common.llm.NoCredentialsException;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterClient implements AiProvider {

    private final WebClient openRouterWebClient;

    @Value("${app.openrouter.api-key:}")
    private String platformApiKey;

    @Value("${app.openrouter.default-model:openrouter/free}")
    private String defaultModel;

    @Override
    public String key() {
        return "openrouter";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String apiKey =
                request.userApiKey() != null
                        && !request.userApiKey().isBlank()
                        ? request.userApiKey()
                        : platformApiKey;

        if (apiKey == null || apiKey.isBlank()) {
            throw new NoCredentialsException(
                    "No OpenRouter API key configured. "
                            + "Connect OpenRouter in Integrations or "
                            + "set app.openrouter.api-key."
            );
        }

        String model =
                request.model() != null
                        && !request.model().isBlank()
                        ? request.model()
                        : defaultModel;

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put("model", model);

        body.put(
                "messages",
                request.messages()
                        .stream()
                        .map(message ->
                                Map.of(
                                        "role",
                                        message.role(),
                                        "content",
                                        message.content()
                                )
                        )
                        .toList()
        );

        if (request.temperature() != null) {
            body.put(
                    "temperature",
                    request.temperature()
            );
        }

        if (request.maxTokens() != null) {
            body.put(
                    "max_tokens",
                    request.maxTokens()
            );
        }

        if (Boolean.TRUE.equals(request.structuredOutput())) {
            body.put(
                    "response_format",
                    Map.of("type", "json_object")
            );
        }

        log.debug(
                "Calling OpenRouter model={}",
                model
        );

        try {
            JsonNode response =
                    openRouterWebClient
                            .post()
                            .uri("/chat/completions")
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + apiKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .bodyValue(body)
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(),
                                    clientResponse ->
                                            clientResponse
                                                    .bodyToMono(
                                                            String.class
                                                    )
                                                    .defaultIfEmpty("")
                                                    .flatMap(
                                                            errorBody ->
                                                                    reactor.core.publisher.Mono
                                                                            .error(
                                                                                    AiProviderException.from(
                                                                                            "openrouter",
                                                                                            clientResponse
                                                                                                    .statusCode()
                                                                                                    .value(),
                                                                                            errorBody
                                                                                    )
                                                                            )
                                                    )
                            )
                            .bodyToMono(JsonNode.class)
                            .timeout(
                                    Duration.ofSeconds(60)
                            )
                            .block();

            if (response == null
                    || !response.has("choices")
                    || response.get("choices").isEmpty()) {
                throw new AiException(
                        "OpenRouter returned an empty response"
                );
            }

            JsonNode message =
                    response
                            .get("choices")
                            .get(0)
                            .get("message");

            if (message == null || message.isNull()) {
                throw new AiException(
                        "OpenRouter returned no assistant message"
                );
            }

            JsonNode contentNode =
                    message.get("content");

            if (contentNode == null
                    || contentNode.isNull()) {
                throw new AiException(
                        "OpenRouter returned no assistant content"
                );
            }

            String content =
                    contentNode.asText();

            if (content.isBlank()) {
                throw new AiException(
                        "OpenRouter returned empty assistant content"
                );
            }

            return new ChatResponse(
                    content,
                    model
            );

        } catch (AiProviderException e) {
            log.warn(
                    "OpenRouter call failed: provider={} status={} code={}",
                    e.getProvider(),
                    e.getHttpStatus(),
                    e.getCode()
            );

            throw e;

        } catch (Exception e) {
            throw new AiException(
                    "OpenRouter request failed",
                    e
            );
        }
    }
}