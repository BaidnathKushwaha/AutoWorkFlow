package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.condition.ConditionEvaluator;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** Safe declarative transformation. No executable expressions or user code. */
@Component
@RequiredArgsConstructor
public class TransformStrategy implements NodeStrategy {
    private final ConditionEvaluator conditionEvaluator;

    /** Backward-compatible constructor for existing unit tests and direct callers. */
    public TransformStrategy() { this(new ConditionEvaluator()); }

    @Override public String getTypeKey() { return "transform"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode input = ctx.getInputPayload();
        JsonNode config = ctx.getNodeConfig();
        ObjectNode output = JsonUtils.mapper().createObjectNode();

        JsonNode mappings = config.path("mappings");
        if (mappings.isArray() && !mappings.isEmpty()) {
            for (JsonNode row : mappings) {
                String target = row.path("output").asText("").trim();
                String source = row.path("source").asText("").trim();
                if (target.isEmpty() || source.isEmpty()) return NodeExecutionResult.failed("Transform mapping requires non-empty output and source paths.");
                JsonNode value = conditionEvaluator.resolvePath(input, source);
                String strip = row.path("strip").asText("");
                if (!strip.isEmpty() && value.isTextual() && value.asText().startsWith(strip)) value = JsonUtils.mapper().getNodeFactory().textNode(value.asText().substring(strip.length()));
                setPath(output, target, value.deepCopy());
            }
        } else if (config.path("mapping").isObject() && config.path("mapping").size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> it = config.path("mapping").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String source = e.getValue().asText("").replaceFirst("^\\$\\.", "").trim();
                if (source.isEmpty()) return NodeExecutionResult.failed("Transform mapping for '" + e.getKey() + "' has an empty source path.");
                setPath(output, e.getKey(), conditionEvaluator.resolvePath(input, source).deepCopy());
            }
        } else {
            if (input != null && input.isObject()) output.setAll((ObjectNode) input.deepCopy());
            else if (input != null) return NodeExecutionResult.ok(input);
        }

        JsonNode conversions = parseJson(config.path("conversions"), "conversions");
        if (conversions != null && conversions.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = conversions.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode current = conditionEvaluator.resolvePath(output, e.getKey());
                if (current.isMissingNode()) continue;
                JsonNode converted = convert(current, e.getValue().asText(""));
                if (converted == null) return NodeExecutionResult.failed("Transform cannot convert field '" + e.getKey() + "' to '" + e.getValue().asText() + "'.");
                setPath(output, e.getKey(), converted);
            }
        }

        JsonNode filter = parseJson(config.path("filter"), "filter");
        if (filter != null && filter.isObject()) {
            String path = filter.path("arrayPath").asText("").trim();
            JsonNode array = conditionEvaluator.resolvePath(output, path);
            JsonNode condition = filter.get("condition");
            if (!array.isArray() || condition == null) return NodeExecutionResult.failed("Transform filter requires arrayPath and condition.");
            conditionEvaluator.validate(condition);
            ArrayNode kept = JsonUtils.mapper().createArrayNode();
            for (JsonNode item : array) if (conditionEvaluator.evaluate(item, condition)) kept.add(item.deepCopy());
            setPath(output, path, kept);
        }

        JsonNode map = parseJson(config.path("map"), "map");
        if (map != null && map.isObject()) {
            String path = map.path("arrayPath").asText("").trim();
            JsonNode array = conditionEvaluator.resolvePath(output, path);
            JsonNode fields = map.get("fields");
            if (!array.isArray() || fields == null || !fields.isObject()) return NodeExecutionResult.failed("Transform map requires arrayPath and a fields object.");
            ArrayNode mapped = JsonUtils.mapper().createArrayNode();
            Map<String, JsonNode> fieldMap = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fieldsIt = fields.fields();
            while (fieldsIt.hasNext()) {
                Map.Entry<String, JsonNode> e = fieldsIt.next();
                fieldMap.put(e.getKey(), e.getValue());
            }
            for (JsonNode item : array) {
                ObjectNode row = JsonUtils.mapper().createObjectNode();
                for (Map.Entry<String, JsonNode> e : fieldMap.entrySet()) row.set(e.getKey(), conditionEvaluator.resolvePath(item, e.getValue().asText()).deepCopy());
                mapped.add(row);
            }
            setPath(output, path, mapped);
        }
        return NodeExecutionResult.ok(output);
    }

    private JsonNode parseJson(JsonNode value, String name) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        if (value.isTextual() && value.asText().isBlank()) return null;
        if (value.isObject() || value.isArray()) return value;
        try { return JsonUtils.mapper().readTree(value.asText()); }
        catch (Exception e) { throw new IllegalArgumentException("Transform " + name + " must contain valid JSON."); }
    }

    private JsonNode convert(JsonNode value, String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "text": case "string": return JsonUtils.mapper().getNodeFactory().textNode(value.isTextual() ? value.asText() : value.toString());
            case "number": case "decimal":
                try { return JsonUtils.mapper().getNodeFactory().numberNode(new BigDecimal(value.asText())); } catch (Exception e) { return null; }
            case "integer": case "int":
                try { return JsonUtils.mapper().getNodeFactory().numberNode(Integer.parseInt(value.asText())); } catch (Exception e) { return null; }
            case "boolean": case "bool":
                if (value.isBoolean()) return value;
                if (value.isTextual() && (value.asText().equalsIgnoreCase("true") || value.asText().equalsIgnoreCase("false"))) return JsonUtils.mapper().getNodeFactory().booleanNode(Boolean.parseBoolean(value.asText()));
                return null;
            default: return null;
        }
    }

    private void setPath(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        if (parts.length == 0 || parts[0].isBlank()) throw new IllegalArgumentException("Transform output path is invalid.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].isBlank()) throw new IllegalArgumentException("Transform output path contains an empty segment.");
            JsonNode child = current.get(parts[i]);
            if (!(child instanceof ObjectNode)) { child = JsonUtils.mapper().createObjectNode(); current.set(parts[i], child); }
            current = (ObjectNode) child;
        }
        current.set(parts[parts.length - 1], value == null ? JsonUtils.mapper().getNodeFactory().nullNode() : value);
    }
}
