package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import org.springframework.stereotype.Component;

@Component
public class GithubEventTriggerStrategy implements NodeStrategy {
    @Override public String getTypeKey() { return "github_event"; }
    @Override public boolean isTrigger() { return true; }
    @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
        return NodeExecutionResult.ok(ctx.getInputPayload());
    }
}
