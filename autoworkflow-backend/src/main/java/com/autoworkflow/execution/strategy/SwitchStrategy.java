package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Generic multi-branch Switch: matches config.field's value in the input payload
 * against config.cases (compared as strings) and returns the matched case as a
 * branchKey — WorkflowExecutor follows only the outgoing edge whose data.branch
 * equals that string, and prunes the rest as dead via the existing resolver.
 *
 * Config: { "field": "match", "cases": ["Strong","Moderate","Weak"], "defaultCase": "Weak" }
 */
@Component
public class SwitchStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "switch"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode input = ctx.getInputPayload();

        String field = config.path("field").asText(null);
        if (field == null || field.isBlank()) {
            return NodeExecutionResult.failed("Switch node is missing required config field 'field'.");
        }

        JsonNode casesNode = config.path("cases");
        if (!casesNode.isArray() || casesNode.isEmpty()) {
            return NodeExecutionResult.failed("Switch node is missing required config field 'cases' (must be a non-empty array).");
        }

        String defaultCase = config.path("defaultCase").asText(null);
        if (defaultCase != null && defaultCase.isBlank()) defaultCase = null;

        JsonNode actualNode = input.path(field);
        if (actualNode.isMissingNode() || actualNode.isNull()) {
            if (defaultCase == null) {
                return NodeExecutionResult.failed("Switch node: input payload has no value for field '" + field
                        + "' and no defaultCase is configured.");
            }
            return NodeExecutionResult.okWithBranchKey(input, defaultCase);
        }

        String actual = actualNode.asText();
        for (JsonNode caseNode : casesNode) {
            if (caseNode.asText().equals(actual)) {
                return NodeExecutionResult.okWithBranchKey(input, actual);
            }
        }

        if (defaultCase != null) {
            return NodeExecutionResult.okWithBranchKey(input, defaultCase);
        }

        return NodeExecutionResult.failed("Switch node: value '" + actual + "' for field '" + field
                + "' did not match any configured case, and no defaultCase is configured.");
    }
}
