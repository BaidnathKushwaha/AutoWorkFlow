package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/** Appends a row to a Google Sheet via the Sheets API values.append endpoint. */
@Component
@RequiredArgsConstructor
public class GoogleSheetsIntegrationStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "google_sheets"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "google_sheets");
        JsonNode config = ctx.getNodeConfig();
        String spreadsheetId = config.path("spreadsheetId").asText();
        String range = config.path("range").asText("Sheet1!A1");

        ArrayNode row = JsonUtils.mapper().createArrayNode();
        JsonNode values = config.path("values");
        if (values.isArray()) {
            values.forEach(row::add);
        } else {
            row.add(ctx.getInputPayload().toString());
        }

        var body = java.util.Map.of("values", java.util.List.of(row));
        String url = "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s:append?valueInputOption=RAW"
                .formatted(spreadsheetId, range);

        JsonNode response = webClientBuilder.build().post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("response", response);
        return NodeExecutionResult.ok(output);
    }
}
