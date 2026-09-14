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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GoogleSheetsIntegrationStrategy implements NodeStrategy {
    private static final String BASE = "https://sheets.googleapis.com/v4/spreadsheets";
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}" );

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
        JsonNode values = parseConfiguredValues(ctx.getNodeConfig().path("values"));
        ArrayNode row = JsonUtils.mapper().createArrayNode();
        if (values != null && values.isArray()) {
            JsonNode source = values.size() == 1 && values.get(0).isArray() ? values.get(0) : values;
            source.forEach(value -> row.add(resolveValue(value, ctx.getInputPayload())));
        } else {
            row.add(ctx.getInputPayload() == null ? "" : ctx.getInputPayload().toString());
        }

        ObjectNode body = JsonUtils.mapper().createObjectNode();
        body.putArray("values").add(row);
        URI uri = appendUri(spreadsheetId, range);
        JsonNode response = apiExecutor.execute("google_sheets", "append row", () -> webClientBuilder.build().post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
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
        JsonNode response = apiExecutor.execute("google_sheets", "read range", () -> webClientBuilder.build().get()
                .uri(valuesUri(spreadsheetId, range))
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
        String expected = resolveString(ctx.getNodeConfig().path("findValue").asText(""), ctx.getInputPayload());
        if (column.isBlank()) throw new IllegalArgumentException("Google Sheets find column is required.");
        JsonNode response = apiExecutor.execute("google_sheets", "find rows", () -> webClientBuilder.build().get()
                .uri(valuesUri(spreadsheetId, range))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());
        int index;
        try { index = Integer.parseInt(column); } catch (NumberFormatException e) { index = columnToIndex(column); }
        if (index < 0) throw new IllegalArgumentException("Invalid Google Sheets find column: " + column);

        ArrayNode matches = JsonUtils.mapper().createArrayNode();
        JsonNode rows = response.path("values");
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            if (row.isArray() && index < row.size() && expected.equals(row.get(index).asText())) {
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

    private JsonNode parseConfiguredValues(JsonNode configured) {
        if (configured == null || configured.isMissingNode() || configured.isNull()) return null;
        if (configured.isArray()) return configured;
        if (!configured.isTextual() || configured.asText().isBlank()) return null;
        try {
            JsonNode parsed = JsonUtils.mapper().readTree(configured.asText());
            if (!parsed.isArray()) throw new IllegalArgumentException("Google Sheets append values must be a JSON array.");
            return parsed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Google Sheets append values must be valid JSON.", e);
        }
    }

    private JsonNode resolveValue(JsonNode value, JsonNode payload) {
        if (!value.isTextual()) return value;
        String template = value.asText();
        Matcher exact = TEMPLATE.matcher(template.trim());
        if (exact.matches()) {
            String key = exact.group(1).trim();
            JsonNode resolved = resolvePath(key, payload);
            if (resolved == null || resolved.isMissingNode()) return JsonUtils.mapper().getNodeFactory().textNode("");
            if (resolved.isObject() || resolved.isArray()) return JsonUtils.mapper().getNodeFactory().textNode(resolved.toString());
            return resolved;
        }
        return JsonUtils.mapper().getNodeFactory().textNode(resolveString(template, payload));
    }

    private JsonNode resolvePath(String key, JsonNode payload) {
        if (payload == null) return null;
        if ("input".equals(key)) return payload;
        return payload.at(key.startsWith("/") ? key : "/" + key.replace('.', '/'));
    }

    private String resolveString(String template, JsonNode payload) {
        Matcher matcher = TEMPLATE.matcher(template == null ? "" : template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            JsonNode value = resolvePath(key, payload);
            String replacement = value == null || value.isMissingNode() || value.isNull()
                    ? ""
                    : (value.isTextual() ? value.asText() : value.toString());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private URI valuesUri(String spreadsheetId, String range) {
        return URI.create(BASE + "/"
                + UriUtils.encodePathSegment(spreadsheetId, StandardCharsets.UTF_8)
                + "/values/"
                + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8));
    }

    private URI appendUri(String spreadsheetId, String range) {
        String path = BASE + "/"
                + UriUtils.encodePathSegment(spreadsheetId, StandardCharsets.UTF_8)
                + "/values/"
                + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8)
                + ":append";
        return UriComponentsBuilder.fromUriString(path)
                .queryParam("valueInputOption", "USER_ENTERED")
                .queryParam("insertDataOption", "INSERT_ROWS")
                .build()
                .toUri();
    }

    private int columnToIndex(String column) {
        int result = 0;
        for (char c : column.toUpperCase().toCharArray()) {
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Invalid Google Sheets column: " + column);
            result = result * 26 + (c - 'A' + 1);
        }
        return result - 1;
    }
}
