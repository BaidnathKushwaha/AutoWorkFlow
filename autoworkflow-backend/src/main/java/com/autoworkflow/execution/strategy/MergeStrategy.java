package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Combines the payloads of every incoming branch. WorkflowExecutor guarantees this
 * node only fires once all live incoming edges have delivered (see WorkflowExecutor's
 * class javadoc for the liveness-propagation rules around untaken IF/AI Router
 * branches), and — unlike the earlier implementation — actually passes all of them
 * in, not just whichever arrived last.
 *
 * Input contract from the executor for any node with more than one incoming edge:
 *   { "inputs": [ <branch 1 output>, <branch 2 output>, ... ] }   (delivery order)
 *
 * Output:
 *   - If every element of `inputs` is a JSON object, they are shallow-merged into a
 *     single object (later branches win on key collisions — documented, not silent:
 *     see `collisions` in the output). This matches the common case of e.g.
 *     { "a": 1 } + { "b": 2 } -> { "a": 1, "b": 2 }.
 *   - Otherwise (any non-object input, e.g. an array or scalar branch output), a safe
 *     merge isn't well-defined, so the raw collected array is returned unchanged under
 *     `merged` instead of guessing.
 */
@Component
public class MergeStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "merge"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode input = ctx.getInputPayload();
        JsonNode inputsArray = input.path("inputs");

        // Single-incoming-edge case (or a workflow that fed Merge a plain payload
        // directly, e.g. during manual testing): nothing to combine, pass through.
        if (!inputsArray.isArray()) {
            return NodeExecutionResult.ok(input);
        }

        boolean allObjects = true;
        for (JsonNode item : inputsArray) {
            if (!item.isObject()) { allObjects = false; break; }
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("itemCount", inputsArray.size());

        if (allObjects) {
            ObjectNode merged = JsonUtils.mapper().createObjectNode();
            java.util.List<String> collidedKeys = new java.util.ArrayList<>();
            for (JsonNode item : inputsArray) {
                item.fields().forEachRemaining(entry -> {
                    if (merged.has(entry.getKey()) && !collidedKeys.contains(entry.getKey())) {
                        collidedKeys.add(entry.getKey());
                    }
                    merged.set(entry.getKey(), entry.getValue());
                });
            }
            output.set("merged", merged);
            if (!collidedKeys.isEmpty()) {
                var arr = output.putArray("collisions");
                collidedKeys.forEach(arr::add);
            }
        } else {
            // Can't safely shallow-merge non-object branch outputs — return them as-is.
            output.set("merged", inputsArray);
        }

        return NodeExecutionResult.ok(output);
    }
}
