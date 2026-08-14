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

/**
 * Generic AI completion node — runs a user-defined prompt through the
 * configured AI provider (OpenAI, Gemini, …).
 *
 * The prompt template supports {{input}} which is replaced with the best
 * available text from the upstream payload. The same 7-step cascade used by
 * SummarizerStrategy is applied so this node works correctly after a
 * WebhookTriggerStrategy, TransformStrategy, or any other upstream node.
 *
 * Type key: "ai"  (legacy alias "openai" → "ai" is handled in NodeStrategyRegistry)
 */
@Component
@RequiredArgsConstructor
public class AiNodeStrategy implements NodeStrategy {

    private final AiService aiService;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "ai"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config  = ctx.getNodeConfig();
        JsonNode payload = ctx.getInputPayload();

        // Resolve the best available text from upstream payload using unified PayloadTextResolver
        String inputText = PayloadTextResolver.resolveTextOrRaw(config, payload);

        String promptTemplate = config.path("prompt").asText(
                "Summarize and explain the following:\n\n{{input}}");
        String prompt = promptTemplate.replace("{{input}}", inputText);

        String provider   = config.path("provider").asText(null);
        String userApiKey = resolveUserKey(ctx, provider);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .userApiKey(userApiKey)
                .userId(ctx.getUserId())
                .model(config.path("model").asText(null))
                .temperature(config.has("temperature")
                        ? config.path("temperature").asDouble() : null)
                .maxTokens(config.has("max_tokens")
                        ? config.path("max_tokens").asInt() : null)
                .build();

        ChatResponse chatResponse = aiService.chat(provider, chatRequest);

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("result",    chatResponse.content());
        output.put("provider",  provider != null ? provider : "default");
        output.put("inputText", inputText.length() > 300
                ? inputText.substring(0, 300) + "…" : inputText);
        if (chatResponse.model() != null) output.put("model", chatResponse.model());
        addAutoRoutingMetadata(output, chatResponse);

        return NodeExecutionResult.ok(output);
    }

    /**
     * Only populated when this call went through AiProviderRouter (provider="auto") —
     * chatResponse.actualProvider() is null for a direct/manual provider call, so this
     * is a no-op for the existing openai/gemini/openrouter behavior. See Phase 8
     * requirement 7's output contract: {"provider":"auto","actualProvider":"openrouter",...}.
     */
    private void addAutoRoutingMetadata(ObjectNode output, ChatResponse chatResponse) {
        if (chatResponse.actualProvider() == null) return;
        output.put("actualProvider", chatResponse.actualProvider());
        if (chatResponse.fallbackUsed()) {
            output.put("fallbackUsed", true);
            var arr = output.putArray("attemptedProviders");
            chatResponse.attemptedProviders().forEach(arr::add);
        }
    }



    private String resolveUserKey(NodeExecutionContext ctx, String provider) {
        // "auto" isn't a real integration provider — AiProviderRouter resolves a fresh
        // per-provider key for each attempt itself (see AiProviderRouter.withResolvedKeyFor),
        // so looking one up here would always fail and is skipped rather than wasting a
        // doomed IntegrationService call.
        if (provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider)) return null;
        try {
            return integrationService.getDecryptedAccessToken(ctx.getUserId(), provider);
        } catch (com.autoworkflow.common.exception.ResourceNotFoundException e) {
            // Not connected / unhealthy -> fall through to the platform key. Any OTHER
            // exception (corrupted token, DB error, etc.) is a real backend problem and
            // must propagate, not be silently treated the same as "not connected".
            return null;
        }
    }
}
