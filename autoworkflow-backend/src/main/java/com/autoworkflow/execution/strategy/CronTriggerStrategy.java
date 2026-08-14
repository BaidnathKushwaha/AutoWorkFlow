package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import org.springframework.stereotype.Component;

/** Trigger nodes don't "execute" logic themselves - the scheduler/webhook layer invokes the
 *  workflow with a payload already attached to this node, so this simply passes it through. */
@Component
public class CronTriggerStrategy implements NodeStrategy {
    @Override public String getTypeKey() { return "cron_trigger"; }
    @Override public boolean isTrigger() { return true; }
    @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
        return NodeExecutionResult.ok(ctx.getInputPayload());
    }
}
