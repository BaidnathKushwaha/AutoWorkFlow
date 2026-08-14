package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Evaluates a simple field/operator/value condition from the node config, e.g.
 * { "field": "status", "operator": "equals", "value": "open" }.
 * The executor uses the returned branchTaken to decide which outgoing edge to follow.
 */
@Component
public class IfConditionStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "if_condition"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode input = ctx.getInputPayload();

        String field = config.path("field").asText(null);
        String operator = config.path("operator").asText("equals");
        String expected = config.path("value").asText(null);

        boolean result;
        if (field == null) {
            result = input.path("value").asBoolean(false);
        } else {
            JsonNode actualNode = input.path(field);
            String actual = actualNode.isMissingNode() ? null : actualNode.asText();
            result = switch (operator) {
                case "equals" -> java.util.Objects.equals(actual, expected);
                case "not_equals" -> !java.util.Objects.equals(actual, expected);
                case "contains" -> actual != null && expected != null && actual.contains(expected);
                case "greater_than" -> actual != null && expected != null && Double.parseDouble(actual) > Double.parseDouble(expected);
                case "less_than" -> actual != null && expected != null && Double.parseDouble(actual) < Double.parseDouble(expected);
                default -> false;
            };
        }
        return NodeExecutionResult.okWithBranch(input, result);
    }
}
