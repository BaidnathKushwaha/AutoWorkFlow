package com.autoworkflow.common.llm.gemini;

import com.autoworkflow.common.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import com.autoworkflow.util.JsonUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient implements AiProvider {

    @Value("${app.gemini.base-url}")
    private String baseUrl;

    @Override
    public String key() { return "gemini"; }

    private final WebClient.Builder webClientBuilder;

    @Value("${app.gemini.api-key:}")
    private String platformApiKey;

    @Value("${app.gemini.default-model:gemini-3.6-flash}")
    private String defaultModel;

    @Override
    public ChatResponse chat(ChatRequest request) {
        String apiKey = (request.userApiKey() != null && !request.userApiKey().isBlank()) ? request.userApiKey() : platformApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new NoCredentialsException("No Gemini API key configured. Connect Gemini in Integrations or set app.gemini.api-key.");
        }

        String model = (request.model() != null && !request.model().isBlank()) ? request.model() : defaultModel;
        if (isLegacyModel(model)) {
            model = defaultModel;
        }

        // Build Gemini request body
        ObjectNode body = JsonUtils.mapper().createObjectNode();
        ArrayNode contents = body.putArray("contents");

        for (ChatMessage msg : request.messages()) {
            ObjectNode content = contents.addObject();
            String role = "system".equals(msg.role()) || "user".equals(msg.role()) ? "user" : "model";
            content.put("role", role);
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", msg.content());
        }

        ObjectNode generationConfig = JsonUtils.mapper().createObjectNode();
        if (request.temperature() != null) {
            generationConfig.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.maxTokens());
        }
        body.set("generationConfig", generationConfig);

        // NOTE: the API key travels as a query param per Google's API contract. It is
        // deliberately never logged (not even the URL) — see AiProviderException for how
        // failures are reported instead. Do not add println/log.info of `url` back in.
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        log.debug("Calling Gemini model={}", model);

        try {
            JsonNode response = webClientBuilder.build().post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .defaultIfEmpty("")
                                            .flatMap(errorBody -> reactor.core.publisher.Mono.error(
                                                    AiProviderException.from("gemini", clientResponse.statusCode().value(), errorBody)))
                    )
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response == null || !response.has("candidates") || response.get("candidates").isEmpty()) {
                throw new AiException("Gemini returned an empty response");
            }

            JsonNode candidate = response.get("candidates").get(0);
            if (candidate.has("content") && candidate.get("content").has("parts") && !candidate.get("content").get("parts").isEmpty()) {
                String text = candidate.get("content").get("parts").get(0).get("text").asText();
                return new ChatResponse(text, model);
            } else {
                throw new AiException("Gemini response candidate content is empty or blocked: " + candidate.path("finishReason").asText("unknown finishReason"));
            }
        } catch (AiProviderException e) {
            log.warn("Gemini call failed: provider={} status={} code={}", e.getProvider(), e.getHttpStatus(), e.getCode());
            throw e;
        } catch (Exception e) {
            throw new AiException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    private boolean isLegacyModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.contains("2.5") || m.contains("1.5") || m.contains("2.0") || m.equals("gemini-flash");
    }
}
