package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.condition.ConditionEvaluator;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Deterministic IF branch resolver backed by the reusable ConditionEvaluator. */
@Component
@RequiredArgsConstructor
public class IfConditionStrategy implements NodeStrategy {
    private final ConditionEvaluator conditionEvaluator;

    @Override public String getTypeKey() { return "if_condition"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        JsonNode input = ctx.getInputPayload();

        JsonNode condition = config.has("condition") ? config.get("condition") : config;
        try {
            conditionEvaluator.validate(condition);
            boolean result = conditionEvaluator.evaluate(input, condition);
            return NodeExecutionResult.okWithBranch(input, result);
        } catch (RuntimeException e) {
            return NodeExecutionResult.failed(e.getMessage() == null ? "Invalid IF condition configuration." : e.getMessage());
        }
    }
}
