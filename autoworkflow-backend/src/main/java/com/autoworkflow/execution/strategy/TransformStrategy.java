package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Safe, no-code data transformation node — field extraction / renaming / selection.
 *
 * This deliberately does NOT execute user-supplied JavaScript: running arbitrary script
 * inside the backend process without a real sandbox (V8 isolate, gVisor, etc.) is an RCE
 * risk, so instead Transform supports a declarative field-mapping config a user builds via
 * a UI (see nodeTypes.js `transform` schema / ConfigPanel's mapping-editor field type):
 *
 * {
 *   "mappings": [
 *     { "output": "repo",    "source": "repository.full_name" },
 *     { "output": "branch",  "source": "ref", "strip": "refs/heads/" },
 *     { "output": "pusher",  "source": "pusher.name" },
 *     { "output": "message", "source": "commits.0.message" }
 *   ]
 * }
 *
 * `source` is a dot-path into the input payload; numeric segments (e.g. "0" in
 * "commits.0.message") index into arrays. `strip` optionally removes a literal
 * prefix from the resolved string value (e.g. turning "refs/heads/main" into "main").
 *
 * The legacy `{ "mapping": { "output": "$.path" } }` object form (no array-index or
 * strip support) is still accepted for backward compatibility with workflows saved
 * before this node existed in its current form.
 *
 * If no mapping is configured at all, Transform passes the input through unchanged —
 * this is deliberate, documented passthrough behavior (e.g. "Webhook -> Transform" is
 * testable before the user has configured any fields), not a silent failure. A row
 * whose `source` is blank/missing is a configuration error and fails the node with a
 * clear message rather than being silently ignored.
 */
@Component
public class TransformStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "transform"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode mappingsNode = config.path("mappings");
        JsonNode legacyMapping = config.path("mapping");

        List<Row> rows = new ArrayList<>();

        if (mappingsNode.isArray() && !mappingsNode.isEmpty()) {
            for (JsonNode row : mappingsNode) {
                String output = row.path("output").asText("").trim();
                String source = row.path("source").asText("").trim();
                String strip = row.has("strip") ? row.path("strip").asText(null) : null;
                if (output.isEmpty() || source.isEmpty()) {
                    return NodeExecutionResult.failed(
                            "Transform mapping row is missing an output field name or source path"
                                    + (output.isEmpty() ? "" : " for output \"" + output + "\""));
                }
                rows.add(new Row(output, source, strip));
            }
        } else if (legacyMapping.isObject() && legacyMapping.size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> fields = legacyMapping.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String source = entry.getValue().asText("").replaceFirst("^\\$\\.", "").trim();
                if (source.isEmpty()) {
                    return NodeExecutionResult.failed("Transform mapping for \"" + entry.getKey() + "\" has an empty source path");
                }
                rows.add(new Row(entry.getKey(), source, null));
            }
        } else {
            // No mapping configured at all -> documented passthrough, not a fabricated transform.
            return NodeExecutionResult.ok(ctx.getInputPayload());
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        for (Row row : rows) {
            JsonNode value = resolvePath(ctx.getInputPayload(), row.source());
            if (row.strip() != null && !row.strip().isEmpty() && value.isTextual()) {
                String text = value.asText();
                if (text.startsWith(row.strip())) {
                    output.put(row.output(), text.substring(row.strip().length()));
                    continue;
                }
            }
            output.set(row.output(), value);
        }
        return NodeExecutionResult.ok(output);
    }

    /**
     * Resolves a dot-path like "commits.0.message" against a JsonNode tree.
     * Numeric segments index into arrays (JsonNode has no string-keyed lookup for
     * ArrayNode — this is exactly the bug the previous implementation had, silently
     * returning MissingNode for any "commits.0.x" style path).
     * Missing fields resolve to a JSON null rather than throwing — a webhook payload
     * legitimately varies shape-to-shape, so an absent optional field isn't an error.
     */
    private JsonNode resolvePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) continue;
            if (current == null || current.isMissingNode() || current.isNull()) {
                return JsonUtils.mapper().nullNode();
            }
            if (current.isArray() && part.matches("\\d+")) {
                current = current.path(Integer.parseInt(part));
            } else {
                current = current.path(part);
            }
        }
        return current == null ? JsonUtils.mapper().nullNode() : current;
    }

    private record Row(String output, String source, String strip) {}
}
