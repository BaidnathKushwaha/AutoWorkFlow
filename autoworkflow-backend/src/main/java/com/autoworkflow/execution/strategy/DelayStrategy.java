package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import org.springframework.stereotype.Component;

@Component
public class DelayStrategy implements NodeStrategy {

    @Override public String getTypeKey() { return "delay"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        long ms = ctx.getNodeConfig().path("durationMs").asLong(1000);
        try {
            Thread.sleep(Math.min(ms, 30_000)); // cap to avoid a runaway execution thread
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return NodeExecutionResult.ok(ctx.getInputPayload());
    }
}
