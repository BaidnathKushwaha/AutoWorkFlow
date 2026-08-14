package com.autoworkflow.execution.engine;

/**
 * One implementation per node type in the marketplace (openai, slack, if_condition, ...).
 * Each node's execution is isolated: a failure in one strategy is caught by
 * the executor and recorded as a failed step without crashing the whole engine
 * unless the node sits on the only path forward.
 */
public interface NodeStrategy {

    /** Must match node_definitions.type_key exactly. */
    String getTypeKey();

    NodeExecutionResult execute(NodeExecutionContext context);

    /**
     * True for trigger strategies (webhook, cron_trigger, github_event, email_received) —
     * the only node types WorkflowExecutor is allowed to seed as an execution start point.
     * A node having zero incoming edges is NOT sufficient on its own to treat it as a
     * trigger: a disconnected/orphaned action node (e.g. a Slack node the user forgot to
     * wire up) also has zero incoming edges, and must NOT silently execute.
     */
    default boolean isTrigger() { return false; }
}
