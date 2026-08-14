package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

/**
 * Iterates over an array found at `arrayField` in the input payload and emits
 * it downstream as-is (each item under `items`); full per-item subgraph
 * re-entry is a natural extension point once nested execution scopes are needed.
 */
@Component
public class LoopStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "loop"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String arrayField = ctx.getNodeConfig().path("arrayField").asText("items");
        JsonNode source = ctx.getInputPayload().path(arrayField);

        ArrayNode items = source.isArray() ? (ArrayNode) source : JsonUtils.mapper().createArrayNode();

        var output = JsonUtils.mapper().createObjectNode();
        output.set("items", items);
        output.put("count", items.size());
        return NodeExecutionResult.ok(output);
    }
}
