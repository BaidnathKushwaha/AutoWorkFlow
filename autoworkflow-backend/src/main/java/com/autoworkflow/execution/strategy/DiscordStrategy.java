package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Posts to Discord via an incoming webhook URL (stored as the "access token"
 * for the discord integration, since Discord channel webhooks don't require
 * per-request OAuth bearer headers).
 */
@Component
@RequiredArgsConstructor
public class DiscordStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "discord"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String webhookUrl = integrationService.getDecryptedAccessToken(ctx.getUserId(), "discord");
        JsonNode config = ctx.getNodeConfig();
        String content = config.path("message").asText(ctx.getInputPayload().toString());

        webClientBuilder.build().post()
                .uri(webhookUrl)
                .bodyValue(java.util.Map.of("content", content))
                .retrieve().toBodilessEntity()
                .timeout(Duration.ofSeconds(30))
                .block();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("posted", true);
        return NodeExecutionResult.ok(output);
    }
}
