package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/** Google Sheets append/read/find integration using the user's connected OAuth token. */
@Component
@RequiredArgsConstructor
public class GoogleSheetsIntegrationStrategy implements NodeStrategy {

    private static final String BASE = "https://sheets.googleapis.com/v4/spreadsheets";

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;
    private final IntegrationApiExecutor apiExecutor;

    @Override public String getTypeKey() { return "google_sheets"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "google_sheets");
        JsonNode config = ctx.getNodeConfig();
        String spreadsheetId = config.path("spreadsheetId").asText().trim();
        String operation = config.path("operation").asText("append").toLowerCase();
        String range = config.path("range").asText("").trim();
        if (spreadsheetId.isBlank()) throw new IllegalArgumentException("Google Sheets spreadsheet ID is required.");
        if (range.isBlank()) throw new IllegalArgumentException("Google Sheets A1 range is required.");

        return switch (operation) {
            case "append" -> append(ctx, token, spreadsheetId, range);
            case "read" -> read(token, spreadsheetId, range);
            case "find" -> find(ctx, token, spreadsheetId, range);
            default -> throw new IllegalArgumentException("Unsupported Google Sheets operation: " + operation);
        };
    }

    private NodeExecutionResult append(NodeExecutionContext ctx, String token, String spreadsheetId, String range) {
        JsonNode values = ctx.getNodeConfig().path("values");
        ArrayNode row = JsonUtils.mapper().createArrayNode();
        if (values.isArray()) {
            if (values.size() == 1 && values.get(0).isArray()) values.get(0).forEach(row::add);
            else values.forEach(row::add);
        } else {
            row.add(ctx.getInputPayload() == null ? "" : ctx.getInputPayload().toString());
        }

        ObjectNode body = JsonUtils.mapper().createObjectNode();
        ArrayNode rows = body.putArray("values");
        rows.add(row);
        String url = BASE + "/" + encodePath(spreadsheetId) + "/values/" + encodeRange(range) + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";

        JsonNode response = apiExecutor.execute("google_sheets", "append row", () ->
                webClientBuilder.build().post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .bodyValue(body).retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("spreadsheetId", spreadsheetId);
        output.put("tableRange", response.path("tableRange").asText(""));
        output.put("updatedRange", response.path("updates").path("updatedRange").asText(""));
        output.put("updatedRows", response.path("updates").path("updatedRows").asInt(0));
        output.put("updatedColumns", response.path("updates").path("updatedColumns").asInt(0));
        output.put("updatedCells", response.path("updates").path("updatedCells").asInt(0));
        return NodeExecutionResult.ok(output);
    }

    private NodeExecutionResult read(String token, String spreadsheetId, String range) {
        JsonNode response = apiExecutor.execute("google_sheets", "read range", () ->
                webClientBuilder.build().get()
                        .uri(BASE + "/" + encodePath(spreadsheetId) + "/values/" + encodeRange(range))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("spreadsheetId", spreadsheetId);
        output.put("range", response.path("range").asText(range));
        output.set("rows", response.path("values").isArray() ? response.path("values") : JsonUtils.mapper().createArrayNode());
        output.put("rowCount", output.path("rows").size());
        return NodeExecutionResult.ok(output);
    }

    private NodeExecutionResult find(NodeExecutionContext ctx, String token, String spreadsheetId, String range) {
        String column = ctx.getNodeConfig().path("findColumn").asText("").trim();
        String expected = ctx.getNodeConfig().path("findValue").asText("");
        if (column.isBlank()) throw new IllegalArgumentException("Google Sheets find column is required.");

        JsonNode response = apiExecutor.execute("google_sheets", "find rows", () ->
                webClientBuilder.build().get()
                        .uri(BASE + "/" + encodePath(spreadsheetId) + "/values/" + encodeRange(range))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());

        int index;
        try { index = Integer.parseInt(column); }
        catch (NumberFormatException e) { index = columnToIndex(column); }

        ArrayNode matches = JsonUtils.mapper().createArrayNode();
        JsonNode rows = response.path("values");
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            if (!row.isArray() || index >= row.size()) continue;
            if (expected.equals(row.get(index).asText())) {
                ObjectNode match = JsonUtils.mapper().createObjectNode();
                match.put("rowIndex", i);
                match.set("values", row);
                matches.add(match);
            }
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("spreadsheetId", spreadsheetId);
        output.put("range", response.path("range").asText(range));
        output.set("matches", matches);
        output.put("count", matches.size());
        return NodeExecutionResult.ok(output);
    }

    private int columnToIndex(String column) {
        int result = 0;
        for (char c : column.toUpperCase().toCharArray()) {
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Invalid Google Sheets column: " + column);
            result = result * 26 + (c - 'A' + 1);
        }
        return result - 1;
    }

    private String encodePath(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
    private String encodeRange(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
}
