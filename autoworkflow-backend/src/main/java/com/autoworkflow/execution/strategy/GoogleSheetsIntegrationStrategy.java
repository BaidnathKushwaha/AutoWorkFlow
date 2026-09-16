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
    private static final Pattern CELL_RANGE = Pattern.compile("(?:(.+)!)?([A-Za-z]+)(\\d+)(?::([A-Za-z]+)(\\d+))?");
    private static final Pattern COLUMN_RANGE = Pattern.compile("(?:(.+)!)?([A-Za-z]+):([A-Za-z]+)");

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
        JsonNode configured = parseConfiguredValues(ctx.getNodeConfig().path("values"));
        boolean createHeaders = ctx.getNodeConfig().path("createHeaders").asBoolean(false);
        ArrayNode row = JsonUtils.mapper().createArrayNode();
        ObjectNode namedMapping = configured != null && configured.isObject() ? (ObjectNode) configured : null;
        boolean headersCreated = false;

        if (configured != null && configured.isArray()) {
            if (createHeaders) {
                throw new IllegalArgumentException("Google Sheets create headers requires a named object mapping.");
            }
            JsonNode source = configured.size() == 1 && configured.get(0).isArray() ? configured.get(0) : configured;
            source.forEach(value -> row.add(resolveValue(value, ctx.getInputPayload())));
        } else if (namedMapping != null) {
            rowFields(namedMapping, row, ctx.getInputPayload());
            if (createHeaders) {
                headersCreated = writeHeadersIfTargetIsEmpty(token, spreadsheetId, range, namedMapping);
            }
        } else {
            if (createHeaders) {
                throw new IllegalArgumentException("Google Sheets create headers requires a named object mapping.");
            }
            row.add(ctx.getInputPayload() == null ? "" : ctx.getInputPayload().toString());
        }

        ObjectNode body = JsonUtils.mapper().createObjectNode();
        body.putArray("values").add(row);
        JsonNode response = apiExecutor.execute("google_sheets", "append row", () -> webClientBuilder.build().post()
                .uri(appendUri(spreadsheetId, range))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("spreadsheetId", spreadsheetId);
        output.put("headersCreated", headersCreated);
        output.put("tableRange", response.path("tableRange").asText(""));
        output.put("updatedRange", response.path("updates").path("updatedRange").asText(""));
        output.put("updatedRows", response.path("updates").path("updatedRows").asInt(0));
        output.put("updatedColumns", response.path("updates").path("updatedColumns").asInt(0));
        output.put("updatedCells", response.path("updates").path("updatedCells").asInt(0));
        return NodeExecutionResult.ok(output);
    }

    private void rowFields(ObjectNode mapping, ArrayNode row, JsonNode payload) {
        mapping.fields().forEachRemaining(entry -> row.add(resolveValue(entry.getValue(), payload)));
    }

    private boolean writeHeadersIfTargetIsEmpty(String token, String spreadsheetId, String range, ObjectNode mapping) {
        String inspectionRange = sheetName(range);
        JsonNode existing = apiExecutor.execute("google_sheets", "check sheet before headers", () -> webClientBuilder.build().get()
                .uri(valuesUri(spreadsheetId, inspectionRange))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());

        JsonNode existingValues = existing.path("values");
        if (existingValues.isArray() && !existingValues.isEmpty()) return false;

        String headerRange = headerRange(range, mapping.size());
        ArrayNode headerRow = JsonUtils.mapper().createArrayNode();
        mapping.fieldNames().forEachRemaining(field -> headerRow.add(humanizeHeader(field)));

        ObjectNode body = JsonUtils.mapper().createObjectNode();
        body.putArray("values").add(headerRow);
        apiExecutor.execute("google_sheets", "create Google Sheets headers", () -> webClientBuilder.build().put()
                .uri(updateValuesUri(spreadsheetId, headerRange))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(body)
                .retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());
        return true;
    }

    private String humanizeHeader(String field) {
        if (field == null || field.isBlank()) return field;
        String words = field.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ').trim();
        StringBuilder result = new StringBuilder();
        for (String word : words.split("\\s+")) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private NodeExecutionResult read(String token, String spreadsheetId, String range) {
        JsonNode response = apiExecutor.execute("google_sheets", "read range", () -> webClientBuilder.build().get()
                .uri(valuesUri(spreadsheetId, range)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block());
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
                .uri(valuesUri(spreadsheetId, range)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block());
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
        if (configured.isArray() || configured.isObject()) return configured;
        if (!configured.isTextual() || configured.asText().isBlank()) return null;
        try {
            JsonNode parsed = JsonUtils.mapper().readTree(configured.asText());
            if (!parsed.isArray() && !parsed.isObject()) throw new IllegalArgumentException("Google Sheets append values must be a JSON array or named object mapping.");
            return parsed;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Google Sheets append values must be valid JSON.", e); }
    }

    private JsonNode resolveValue(JsonNode value, JsonNode payload) {
        if (!value.isTextual()) return value;
        String template = value.asText();
        Matcher exact = TEMPLATE.matcher(template.trim());
        if (exact.matches()) {
            JsonNode resolved = resolvePath(exact.group(1).trim(), payload);
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
            JsonNode value = resolvePath(matcher.group(1).trim(), payload);
            String replacement = value == null || value.isMissingNode() || value.isNull() ? "" : (value.isTextual() ? value.asText() : value.toString());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private URI valuesUri(String spreadsheetId, String range) {
        return URI.create(BASE + "/" + UriUtils.encodePathSegment(spreadsheetId, StandardCharsets.UTF_8) + "/values/" + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8));
    }

    private URI updateValuesUri(String spreadsheetId, String range) {
        return UriComponentsBuilder.fromUriString(BASE + "/" + UriUtils.encodePathSegment(spreadsheetId, StandardCharsets.UTF_8) + "/values/" + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8)).queryParam("valueInputOption", "USER_ENTERED").build().toUri();
    }

    private URI appendUri(String spreadsheetId, String range) {
        String path = BASE + "/" + UriUtils.encodePathSegment(spreadsheetId, StandardCharsets.UTF_8) + "/values/" + UriUtils.encodePathSegment(range, StandardCharsets.UTF_8) + ":append";
        return UriComponentsBuilder.fromUriString(path).queryParam("valueInputOption", "USER_ENTERED").queryParam("insertDataOption", "INSERT_ROWS").build().toUri();
    }

    private String sheetName(String range) {
        int separator = range.lastIndexOf('!');
        return separator > 0 ? range.substring(0, separator) : range;
    }

    private String headerRange(String range, int columnCount) {
        int separator = range.lastIndexOf('!');
        String sheet = separator > 0 ? range.substring(0, separator) : range;
        String a1 = separator > 0 ? range.substring(separator + 1) : "A1";
        Matcher cellMatcher = CELL_RANGE.matcher(a1);
        String startColumn = "A";
        String startRow = "1";
        if (cellMatcher.matches()) {
            startColumn = cellMatcher.group(2);
            startRow = cellMatcher.group(3);
        } else {
            Matcher columnMatcher = COLUMN_RANGE.matcher(a1);
            if (columnMatcher.matches()) startColumn = columnMatcher.group(2);
        }
        String endColumn = columnAfter(startColumn, Math.max(columnCount, 1) - 1);
        return sheet + "!" + startColumn + startRow + ":" + endColumn + startRow;
    }

    private String columnAfter(String startColumn, int offset) {
        int number = columnToIndex(startColumn) + offset + 1;
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            int remainder = (number - 1) % 26;
            result.append((char) ('A' + remainder));
            number = (number - 1) / 26;
        }
        return result.reverse().toString();
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
