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
 * AI Summariser node — extracts text from the upstream payload and asks the
 * configured provider to summarize it.
 *
 * Text resolution order (all explicit, no silent last-resort unless enabled):
 *   1. inputText config field (literal text / {{input}} template)
 *   2. textField config field  → dot-path lookup into the input payload
 *      (supports nested paths like "data.text" and array indices like "commits.0.message")
 *   3. payload.text            (set by WebhookTriggerStrategy for all events)
 *   4. If none of the above resolve to text AND `allowRawFallback` is not explicitly
 *      true in config, the node FAILS with a clear message instead of silently
 *      stringifying the entire JSON payload (which used to hide missing configuration).
 *
 * maxLength vs maxTokens: `maxLength` is a character budget for the *output* summary
 * (used in the prompt and to hard-truncate the result) — it is NOT sent to the provider
 * as a token count. A separate, derived token budget is computed for the actual API
 * call so "200 characters" doesn't silently become "200 tokens" (roughly 4x too generous).
 */
@Component
@RequiredArgsConstructor
public class SummarizerStrategy implements NodeStrategy {

    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;
    private static final int MIN_TOKEN_BUDGET = 64;

    private final AiService aiService;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "summarizer"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config  = ctx.getNodeConfig();
        JsonNode payload = ctx.getInputPayload();

        String text;
        try {
            text = PayloadTextResolver.resolveText(config, payload, false);
        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failed(e.getMessage());
        }

        // An unset provider means "use my account-level AI preference" (AiService's
        // "default" sentinel), NOT the static app.ai.default-provider config — a node's
        // own explicit choice (including an explicit "auto") still takes precedence
        // over that preference, per the AI preference precedence rules.
        String provider    = config.path("provider").asText("default");
        String model       = config.has("model") && !config.path("model").asText().isEmpty()
                ? config.path("model").asText() : null;
        int    rawMaxLength = config.path("maxLength").asInt(200);
        int    maxLength    = Math.min(10000, Math.max(20, rawMaxLength));
        // Token budget is DERIVED from the character budget, not the same number —
        // see class javadoc. A floor keeps very short maxLength values from starving
        // the model mid-sentence.
        int    maxTokens   = Math.max(MIN_TOKEN_BUDGET, maxLength / CHARS_PER_TOKEN_ESTIMATE * 2);
        String userApiKey  = resolveUserKey(ctx, provider);

        String systemPrompt = "You are an expert summarizer. Be concise, clear, and accurate.";
        String userPrompt   = "Summarize the following within approximately " + maxLength
                + " characters:\n\n" + text;

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)
                ))
                .userApiKey(userApiKey)
                .userId(ctx.getUserId())
                .model(model)
                .maxTokens(maxTokens)
                .build();

        // Provider failures (quota, auth, etc.) are NOT caught here — they propagate as
        // AiProviderException/AiException with a clean message and are surfaced by the
        // WorkflowExecutor as this node's LogStep.error, exactly like any other node
        // failure. Swallowing them here would hide real configuration problems.
        ChatResponse response = aiService.chat(provider, request);

        String summary = response.content();
        if (summary != null && summary.length() > maxLength) {
            summary = summary.substring(0, maxLength).stripTrailing() + "…";
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("summary",  summary);
        output.put("provider", provider != null ? provider : "default");
        output.put("inputText", text.length() > 300 ? text.substring(0, 300) + "…" : text);
        if (response.model() != null) output.put("model", response.model());
        if (response.actualProvider() != null) {
            output.put("actualProvider", response.actualProvider());
            if (response.fallbackUsed()) {
                output.put("fallbackUsed", true);
                var arr = output.putArray("attemptedProviders");
                response.attemptedProviders().forEach(arr::add);
            }
        }

        return NodeExecutionResult.ok(output);
    }

    private String resolveUserKey(NodeExecutionContext ctx, String provider) {
        // Neither "auto" nor "default" is a real integration provider. AUTO mode has
        // AiProviderRouter resolve a fresh per-provider key for each attempt itself;
        // "default" resolves to the user's saved AI preference inside AiService, which
        // isn't known yet at this point in the strategy. Looking either up here would
        // always fail, so both are skipped rather than wasting a doomed lookup.
        if (provider == null || provider.isBlank()
                || "auto".equalsIgnoreCase(provider)
                || "default".equalsIgnoreCase(provider)) return null;
        try {
            return integrationService.getDecryptedAccessToken(ctx.getUserId(), provider);
        } catch (com.autoworkflow.common.exception.ResourceNotFoundException e) {
            return null;
        }
    }
}