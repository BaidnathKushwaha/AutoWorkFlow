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

import java.util.ArrayList;
import java.util.List;

/**
 * Uses an LLM to pick one of the branch labels configured on the node
 * (config.branches: ["urgent","normal"]) and returns which branch to take
 * as a boolean (true = first branch) for the executor's edge-following logic.
 * For >2 branches, extend NodeExecutionResult with a String branch label.
 */
@Component
@RequiredArgsConstructor
public class AiRouterStrategy implements NodeStrategy {

    private final AiService aiService;

    @Override
    public String getTypeKey() {
        return "ai_router";
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        List<String> branches = new ArrayList<>();

        ctx.getNodeConfig()
                .path("branches")
                .forEach(b -> branches.add(b.asText()));

        if (branches.size() < 2) {
            branches.clear();
            branches.add("true");
            branches.add("false");
        }

        String inputText = com.autoworkflow.util.PayloadTextResolver.resolveTextOrRaw(ctx.getNodeConfig(), ctx.getInputPayload());
        String prompt = "Given this input, choose exactly one of these options: " + branches
                + ". Respond with only the chosen option text.\nInput: " + inputText;

        // No hardcoded fallback here — a blank/missing provider is resolved centrally by
        // AiService using app.ai.default-provider, so this strategy stays provider-agnostic.
        String provider = ctx.getNodeConfig()
                .path("provider")
                .asText(null);

        ChatResponse chatResponse = aiService.chat(provider, ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .model(ctx.getNodeConfig().path("model").asText(null))
                .userId(ctx.getUserId())
                .build());
        
        String decision = chatResponse.content().trim();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("input", ctx.getInputPayload());
        output.put("route", decision);
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

        boolean tookFirstBranch = decision.equalsIgnoreCase(branches.get(0));
        return NodeExecutionResult.okWithBranch(output, tookFirstBranch);
    }
}

