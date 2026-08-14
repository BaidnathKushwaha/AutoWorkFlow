package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/** Creates a new page in a connected Notion database. Powers the "Trend Generator" template's publish step. */
@Component
@RequiredArgsConstructor
public class NotionIntegrationStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "notion"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "notion");
        JsonNode config = ctx.getNodeConfig();
        String databaseId = config.path("databaseId").asText();
        String titleField = config.path("titleField").asText("title");
        String title = ctx.getInputPayload().path(titleField).asText("Untitled");

        var body = java.util.Map.of(
                "parent", java.util.Map.of("database_id", databaseId),
                "properties", java.util.Map.of("Name", java.util.Map.of(
                        "title", java.util.List.of(java.util.Map.of("text", java.util.Map.of("content", title)))))
        );

        JsonNode response = webClientBuilder.build().post()
                .uri("https://api.notion.com/v1/pages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Notion-Version", "2022-06-28")
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("response", response);
        return NodeExecutionResult.ok(output);
    }
}
