package com.autoworkflow.execution.strategy;

import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Powers the "AI Email Router" template's classify-intent step. */
@Component
@RequiredArgsConstructor
public class ClassifierStrategy implements NodeStrategy {

    private final AiService aiService;

    @Override public String getTypeKey() { return "classifier"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        List<String> labels = new java.util.ArrayList<>();
        ctx.getNodeConfig().path("labels").forEach(l -> labels.add(l.asText()));
        List<String> resolvedLabels = labels.isEmpty() ? List.of("support", "sales", "spam", "other") : labels;

        String text = com.autoworkflow.util.PayloadTextResolver.resolveTextOrRaw(ctx.getNodeConfig(), ctx.getInputPayload());

        String prompt = "Classify the following text into exactly one of these labels: " + resolvedLabels +
                ". Respond with only the label.\n\nText: " + text;

        // No hardcoded fallback here — a blank/missing provider is resolved centrally by
        // AiService using app.ai.default-provider, so this strategy stays provider-agnostic.
        String provider = ctx.getNodeConfig()
                .path("provider")
                .asText(null);

        ChatResponse chatResponse = aiService.chat(
                provider,
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .model(ctx.getNodeConfig().path("model").asText(null))
                        .userId(ctx.getUserId())
                        .build()
        );
        
        String label = chatResponse.content().trim();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("input", ctx.getInputPayload());
        output.put("label", label);
        output.put("provider", provider != null ? provider : "default");
        if (chatResponse.model() != null) output.put("model", chatResponse.model());
        if (chatResponse.actualProvider() != null) {
            output.put("actualProvider", chatResponse.actualProvider());
            if (chatResponse.fallbackUsed()) {
                output.put("fallbackUsed", true);
                var arr = output.putArray("attemptedProviders");
                chatResponse.attemptedProviders().forEach(arr::add);
            }
        }
        return NodeExecutionResult.ok(output);
    }
}

