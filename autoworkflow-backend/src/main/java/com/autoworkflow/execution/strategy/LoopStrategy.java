package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

/**
 * Loop controller. WorkflowExecutor owns the scoped body execution. This strategy only
 * resolves and validates the collection, keeping iteration state out of node instances.
 *
 * Config: { "arrayField": "items" }
 * Output: { "items": [...], "count": N }
 */
@Component
public class LoopStrategy implements NodeStrategy {
    @Override public String getTypeKey() { return "loop"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String arrayField = ctx.getNodeConfig().path("arrayField").asText("items").trim();
        if (arrayField.isEmpty()) return NodeExecutionResult.failed("Loop node requires a non-empty 'arrayField' path.");

        JsonNode source = resolvePath(ctx.getInputPayload(), arrayField);
        if (source.isMissingNode() || source.isNull()) {
            return NodeExecutionResult.failed("Loop node could not find array at path '" + arrayField + "'.");
        }
        if (!source.isArray()) {
            return NodeExecutionResult.failed("Loop node expected an array at path '" + arrayField + "' but received " + source.getNodeType().name().toLowerCase() + ".");
        }

        ArrayNode items = ((ArrayNode) source).deepCopy();
        var output = JsonUtils.mapper().createObjectNode();
        output.set("items", items);
        output.put("count", items.size());
        return NodeExecutionResult.ok(output);
    }

    private JsonNode resolvePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (part.isBlank() || current == null || current.isMissingNode() || current.isNull()) {
                return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            }
            if (current.isArray() && part.matches("\\d+")) current = current.path(Integer.parseInt(part));
            else current = current.path(part);
        }
        return current == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : current;
    }
}
