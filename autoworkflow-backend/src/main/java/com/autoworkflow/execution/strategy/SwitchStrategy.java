package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.condition.ConditionEvaluator;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Typed field-path Switch. Each selected case is represented by branchKey. */
@Component
@RequiredArgsConstructor
public class SwitchStrategy implements NodeStrategy {
    private final ConditionEvaluator conditionEvaluator;

    /** Backward-compatible constructor for existing unit tests and direct callers. */
    public SwitchStrategy() {
        this(new ConditionEvaluator());
    }

    @Override public String getTypeKey() { return "switch"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode input = ctx.getInputPayload();
        String field = config.path("field").asText("").trim();
        if (field.isEmpty()) return NodeExecutionResult.failed("Switch node is missing required config field 'field'.");

        java.util.List<String> cases = new java.util.ArrayList<>();
        JsonNode casesNode = config.path("cases");
        if (casesNode.isArray()) for (JsonNode c : casesNode) {
            String value = c.asText("").trim();
            if (!value.isEmpty() && !cases.contains(value)) cases.add(value);
        }
        if (cases.isEmpty()) return NodeExecutionResult.failed("Switch node requires at least one non-empty case.");

        String defaultCase = config.path("defaultCase").asText("").trim();
        JsonNode actual = conditionEvaluator.resolvePath(input, field);
        if (actual.isMissingNode() || actual.isNull()) {
            if (!defaultCase.isEmpty()) return NodeExecutionResult.okWithBranchKey(input, defaultCase);
            return NodeExecutionResult.failed("Switch node: field '" + field + "' is missing/null and no defaultCase is configured.");
        }

        for (String caseValue : cases) {
            if (caseMatches(actual, caseValue)) return NodeExecutionResult.okWithBranchKey(input, caseValue);
        }
        if (!defaultCase.isEmpty()) return NodeExecutionResult.okWithBranchKey(input, defaultCase);
        return NodeExecutionResult.failed("Switch node: value for field '" + field + "' did not match any configured case and no defaultCase is configured.");
    }

    private boolean caseMatches(JsonNode actual, String expected) {
        if (actual.isTextual()) return actual.asText().equals(expected);
        if (actual.isNumber()) {
            try { return actual.decimalValue().compareTo(new java.math.BigDecimal(expected)) == 0; } catch (Exception ignored) { return false; }
        }
        if (actual.isBoolean()) return Boolean.toString(actual.booleanValue()).equalsIgnoreCase(expected);
        return actual.toString().equals(expected);
    }
}
