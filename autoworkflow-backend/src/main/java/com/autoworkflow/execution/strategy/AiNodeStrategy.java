package com.autoworkflow.execution.strategy;

import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.util.PayloadTextResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AiNodeStrategy implements NodeStrategy {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}" );
    private final AiService aiService;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "ai"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode payload = ctx.getInputPayload();
        String inputText = PayloadTextResolver.resolveTextOrRaw(config, payload);
        String promptTemplate = config.path("prompt").asText("Summarize and explain the following:\n\n{{input}}");
        String prompt = substituteTemplate(promptTemplate, payload, inputText);
        String provider = config.path("provider").asText("default");
        String userApiKey = resolveUserKey(ctx, provider);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .userApiKey(userApiKey)
                .userId(ctx.getUserId())
                .model(config.path("model").asText(null))
                .temperature(config.has("temperature") ? config.path("temperature").asDouble() : null)
                .maxTokens(config.has("max_tokens") ? config.path("max_tokens").asInt() : null)
                .structuredOutput(config.path("structuredOutput").asBoolean(false))
                .build();

        ChatResponse chatResponse = aiService.chat(provider, chatRequest);
        ObjectNode output = JsonUtils.mapper().createObjectNode();
        String content = chatResponse.content();
        output.put("result", content);
        output.put("provider", provider);
        output.put("inputText", inputText.length() > 300 ? inputText.substring(0, 300) + "…" : inputText);
        if (chatResponse.model() != null) output.put("model", chatResponse.model());
        if (config.path("structuredOutput").asBoolean(false)) {
            try {
                JsonNode structured = JsonUtils.mapper().readTree(content);
                if (structured != null && structured.isObject()) output.setAll((ObjectNode) structured);
            } catch (Exception e) {
                throw new IllegalArgumentException("AI structured output was not valid JSON.", e);
            }
        }
        addAutoRoutingMetadata(output, chatResponse);
        return NodeExecutionResult.ok(output);
    }

    private String substituteTemplate(String template, JsonNode payload, String inputText) {
        Matcher matcher = TEMPLATE.matcher(template == null ? "" : template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement;
            if ("input".equals(key)) replacement = inputText;
            else {
                JsonNode value = payload == null ? null : payload.at(key.startsWith("/") ? key : "/" + key.replace('.', '/'));
                replacement = value == null || value.isMissingNode() ? "" : (value.isTextual() ? value.asText() : value.toString());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void addAutoRoutingMetadata(ObjectNode output, ChatResponse response) {
        if (response.actualProvider() == null) return;
        output.put("actualProvider", response.actualProvider());
        if (response.fallbackUsed()) {
            output.put("fallbackUsed", true);
            var arr = output.putArray("attemptedProviders");
            response.attemptedProviders().forEach(arr::add);
        }
    }

    private String resolveUserKey(NodeExecutionContext ctx, String provider) {
        if (provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider) || "default".equalsIgnoreCase(provider)) return null;
        try {
            return integrationService.getDecryptedAccessToken(ctx.getUserId(), provider);
        } catch (com.autoworkflow.common.exception.ResourceNotFoundException e) {
            return null;
        }
    }
}
