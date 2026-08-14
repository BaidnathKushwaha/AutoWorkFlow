package com.autoworkflow.common.llm.openai;

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
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiClient implements AiProvider {

    @Override
    public String key() { return "openai"; }

    private final WebClient openAiWebClient;

    @Value("${app.openai.api-key}")
    private String platformApiKey;

    @Value("${app.openai.default-model}")
    private String defaultModel;

    @Override
    public ChatResponse chat(ChatRequest request) {
        String apiKey = (request.userApiKey() != null && !request.userApiKey().isBlank()) ? request.userApiKey() : platformApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new NoCredentialsException("No OpenAI API key configured. Connect OpenAI in Integrations or set app.openai.api-key.");
        }

        String model = (request.model() != null && !request.model().isBlank()) ? request.model() : defaultModel;
        double temp = request.temperature() != null ? request.temperature() : 0.7;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temp,
                "messages", request.messages().stream()
                        .map(m -> Map.of("role", m.role(), "content", m.content()))
                        .toList()
        );

        log.debug("Calling OpenAI model={}", model);

        try {
            JsonNode response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .defaultIfEmpty("")
                                            .flatMap(errorBody -> reactor.core.publisher.Mono.error(
                                                    AiProviderException.from("openai", clientResponse.statusCode().value(), errorBody)))
                    )
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                throw new AiException("OpenAI returned an empty response");
            }
            String content = response.get("choices").get(0).get("message").get("content").asText();
            return new ChatResponse(content, model);
        } catch (AiProviderException e) {
            log.warn("OpenAI call failed: provider={} status={} code={}", e.getProvider(), e.getHttpStatus(), e.getCode());
            throw e;
        } catch (Exception e) {
            throw new AiException("OpenAI request failed: " + e.getMessage(), e);
        }
    }
}
