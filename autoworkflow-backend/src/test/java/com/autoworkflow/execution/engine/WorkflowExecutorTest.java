package com.autoworkflow.execution.engine;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase 27's required WorkflowExecutor scenarios: a linear A->B->C chain,
 * IF true/false branching, Merge waiting for + actually receiving both incoming
 * branches, and that a disconnected non-trigger node never silently executes.
 */
class WorkflowExecutorTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKFLOW_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    /** A real trigger strategy stand-in (isTrigger=true) — passes the payload through. */
    private NodeStrategy triggerStrategy(String typeKey) {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return typeKey; }
            @Override public boolean isTrigger() { return true; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                return NodeExecutionResult.ok(ctx.getInputPayload());
            }
        };
    }

    /** Echoes the input back with `visitedBy` set to this node's id — lets tests assert exact pass-through. */
    private NodeStrategy echoStrategy(String typeKey) {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return typeKey; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                JsonNode in = ctx.getInputPayload();
                ObjectNode out = in.isObject() ? in.deepCopy() : JsonUtils.mapper().createObjectNode();
                out.put("visitedBy", ctx.getNodeId());
                return NodeExecutionResult.ok(out);
            }
        };
    }

    /** A regular (non-trigger) action strategy stand-in, e.g. "slack" — should never run if disconnected. */
    private final AtomicInteger slackRunCount = new AtomicInteger(0);

    private NodeStrategy fakeSlack() {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return "slack"; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                slackRunCount.incrementAndGet();
                return NodeExecutionResult.ok(ctx.getInputPayload());
            }
        };
    }

    /** Fake IF strategy: branches based on config.value == "true". */
    private NodeStrategy fakeIf() {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return "if_condition"; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                boolean branch = ctx.getNodeConfig().path("value").asBoolean(false);
                return NodeExecutionResult.okWithBranch(ctx.getInputPayload(), branch);
            }
        };
    }

    /** Fake Switch strategy: returns config.branchKey as a named branch. */
    private NodeStrategy fakeSwitch() {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return "switch"; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                String key = ctx.getNodeConfig().path("branchKey").asText();
                return NodeExecutionResult.okWithBranchKey(ctx.getInputPayload(), key);
            }
        };
    }

    /** Fake Merge strategy: records how many times it ran and exactly what input it received. */
    private final AtomicInteger mergeRunCount = new AtomicInteger(0);
    private JsonNode lastMergeInput;

    private NodeStrategy fakeMerge() {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return "merge"; }
            @Override public NodeExecutionResult execute(NodeExecutionContext ctx) {
                mergeRunCount.incrementAndGet();
                lastMergeInput = ctx.getInputPayload();
                return NodeExecutionResult.ok(ctx.getInputPayload());
            }
        };
    }

    private ObjectNode node(String id, String type) {
        ObjectNode n = JsonUtils.mapper().createObjectNode();
        n.put("id", id);
        n.put("type", type);
        n.putObject("data").put("label", id);
        return n;
    }

    private ObjectNode edge(String source, String target, String branch) {
        ObjectNode e = JsonUtils.mapper().createObjectNode();
        e.put("id", source + "-" + target);
        e.put("source", source);
        e.put("target", target);
        if (branch != null) {
            e.putObject("data").put("branch", branch);
        }
        return e;
    }

    @Test
    void linearWorkflow_threadsOutputAsNextNodesInput() {
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), echoStrategy("transform"), echoStrategy("summarizer"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("A", "webhook"));
        nodes.add(node("B", "transform"));
        nodes.add(node("C", "summarizer"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("A", "B", null));
        edges.add(edge("B", "C", null));

        JsonNode trigger = JsonUtils.mapper().createObjectNode().put("text", "hello");
        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges, trigger);

        assertThat(result.success()).isTrue();
        assertThat(result.steps()).hasSize(3);

        LogStep stepA = result.steps().get(0);
        LogStep stepB = result.steps().get(1);
        LogStep stepC = result.steps().get(2);

        assertThat(stepB.getInputPayload()).isEqualTo(stepA.getOutputPayload());
        assertThat(stepC.getInputPayload()).isEqualTo(stepB.getOutputPayload());
        assertThat(stepC.getOutputPayload().get("visitedBy").asText()).isEqualTo("C");
    }

    @Test
    void disconnectedNonTriggerNode_doesNotExecute() {
        // Webhook -> Transform is the real workflow; a Slack node sits on the canvas with
        // zero incoming edges (user forgot to wire it up). Only Webhook has isTrigger=true,
        // so Slack must never be seeded/executed even though it also has 0 incoming edges.
        slackRunCount.set(0);
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), echoStrategy("transform"), fakeSlack())));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Webhook", "webhook"));
        nodes.add(node("Transform", "transform"));
        nodes.add(node("Slack", "slack")); // disconnected: no edges reference it at all

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Webhook", "Transform", null));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        assertThat(slackRunCount.get()).isZero();
        List<String> executedNodeIds = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(executedNodeIds).containsExactly("Webhook", "Transform");
    }

    @Test
    void noTriggerAnywhere_fallsBackToZeroIncomingNodesAsManualStartPoints() {
        // This is the Phase 10 fix: a workflow with NO trigger node at all is a valid
        // manual/test execution (e.g. a standalone Summarizer configured with Direct
        // Input Text) — WorkflowValidator.validateForExecution already allows this
        // structurally; here the executor must actually run it, starting from every
        // zero-incoming node, instead of failing with "no trigger node".
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(echoStrategy("transform"), echoStrategy("summarizer"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Summarizer", "summarizer")); // zero incoming, NOT a trigger type
        nodes.add(node("Transform", "transform"));   // fed by Summarizer

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Summarizer", "Transform", null));

        JsonNode manualPayload = JsonUtils.mapper().createObjectNode().put("text", "manual test input");
        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges, manualPayload);

        assertThat(result.success()).isTrue();
        List<String> executedNodeIds = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(executedNodeIds).containsExactly("Summarizer", "Transform");
        // Summarizer, as the manual root, received the manual Run payload directly.
        assertThat(result.steps().get(0).getInputPayload()).isEqualTo(manualPayload);
    }

    @Test
    void noValidStartingNode_stillFailsClearly_whenEveryNodeHasAnIncomingEdge() {
        // Distinct from the fallback case above: here NOTHING is a valid start point at
        // all (every node has >=1 incoming edge, e.g. a raw 2-cycle) — there is no
        // zero-incoming node to fall back to, so this must still fail clearly rather
        // than silently doing nothing or picking an arbitrary node mid-cycle.
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(echoStrategy("transform"), echoStrategy("summarizer"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("A", "transform"));
        nodes.add(node("B", "summarizer"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("A", "B", null));
        edges.add(edge("B", "A", null)); // every node now has an incoming edge

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("starting node");
    }

    @Test
    void ifTrueBranch_onlyExecutesTrueTarget() {
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), fakeIf(), echoStrategy("trueBranchNode"), echoStrategy("falseBranchNode"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        ObjectNode ifNode = node("If", "if_condition");
        ifNode.putObject("data").put("value", true);
        nodes.add(ifNode);
        nodes.add(node("TrueNode", "trueBranchNode"));
        nodes.add(node("FalseNode", "falseBranchNode"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "If", null));
        edges.add(edge("If", "TrueNode", "true"));
        edges.add(edge("If", "FalseNode", "false"));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        List<String> executedNodeIds = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(executedNodeIds).contains("Trigger", "If", "TrueNode");
        assertThat(executedNodeIds).doesNotContain("FalseNode");
    }

    @Test
    void ifFalseBranch_onlyExecutesFalseTarget() {
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), fakeIf(), echoStrategy("trueBranchNode"), echoStrategy("falseBranchNode"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        ObjectNode ifNode = node("If", "if_condition");
        ifNode.putObject("data").put("value", false);
        nodes.add(ifNode);
        nodes.add(node("TrueNode", "trueBranchNode"));
        nodes.add(node("FalseNode", "falseBranchNode"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "If", null));
        edges.add(edge("If", "TrueNode", "true"));
        edges.add(edge("If", "FalseNode", "false"));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        List<String> executedNodeIds = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(executedNodeIds).contains("Trigger", "If", "FalseNode");
        assertThat(executedNodeIds).doesNotContain("TrueNode");
    }

    @Test
    void merge_waitsForBothIncomingBranchesBeforeExecuting() {
        mergeRunCount.set(0);
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), echoStrategy("branchA"), echoStrategy("branchB"), fakeMerge())));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        nodes.add(node("A", "branchA"));
        nodes.add(node("B", "branchB"));
        nodes.add(node("Merge", "merge"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "A", null));
        edges.add(edge("Trigger", "B", null));
        edges.add(edge("A", "Merge", null));
        edges.add(edge("B", "Merge", null));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        assertThat(mergeRunCount.get()).isEqualTo(1);

        long mergeSteps = result.steps().stream().filter(s -> s.getNodeId().equals("Merge")).count();
        assertThat(mergeSteps).isEqualTo(1);

        List<String> order = result.steps().stream().map(LogStep::getNodeId).toList();
        int mergeIndex = order.indexOf("Merge");
        assertThat(order.indexOf("A")).isLessThan(mergeIndex);
        assertThat(order.indexOf("B")).isLessThan(mergeIndex);
    }

    @Test
    void merge_actuallyReceivesBothBranchOutputs_notJustWhicheverArrivedLast() {
        // This is the important assertion the earlier "waits" test alone did NOT cover:
        // Merge's input must contain BOTH A's and B's output, not just the last one in.
        mergeRunCount.set(0);
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), echoStrategy("branchA"), echoStrategy("branchB"), fakeMerge())));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        nodes.add(node("A", "branchA"));
        nodes.add(node("B", "branchB"));
        nodes.add(node("Merge", "merge"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "A", null));
        edges.add(edge("Trigger", "B", null));
        edges.add(edge("A", "Merge", null));
        edges.add(edge("B", "Merge", null));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        assertThat(lastMergeInput.has("inputs")).isTrue();
        assertThat(lastMergeInput.get("inputs").isArray()).isTrue();
        assertThat(lastMergeInput.get("inputs")).hasSize(2);

        List<String> visitedByValues = new java.util.ArrayList<>();
        lastMergeInput.get("inputs").forEach(item -> visitedByValues.add(item.get("visitedBy").asText()));
        assertThat(visitedByValues).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void mergeDownstreamOfIf_doesNotWaitForeverOnTheUntakenBranch() {
        // Trigger -> If -(true)-> A -> Merge
        //               -(false)-> B -> Merge
        // Only one of A/B ever runs, so Merge's required-incoming count must be pruned
        // down to 1 for the branch that WAS taken, or it would wait forever.
        mergeRunCount.set(0);
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), fakeIf(), echoStrategy("branchA"), echoStrategy("branchB"), fakeMerge())));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        ObjectNode ifNode = node("If", "if_condition");
        ifNode.putObject("data").put("value", true);
        nodes.add(ifNode);
        nodes.add(node("A", "branchA"));
        nodes.add(node("B", "branchB"));
        nodes.add(node("Merge", "merge"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "If", null));
        edges.add(edge("If", "A", "true"));
        edges.add(edge("If", "B", "false"));
        edges.add(edge("A", "Merge", null));
        edges.add(edge("B", "Merge", null));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode());

        assertThat(result.success()).isTrue();
        assertThat(mergeRunCount.get()).isEqualTo(1);
        List<String> order = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(order).contains("A", "Merge").doesNotContain("B");

        // And Merge only received the one branch that actually ran.
        assertThat(lastMergeInput.get("inputs")).hasSize(1);
        assertThat(lastMergeInput.get("inputs").get(0).get("visitedBy").asText()).isEqualTo("A");
    }

    @Test
    void switchNode_followsOnlyTheEdgeMatchingItsBranchKey() {
        // Trigger -> Switch(branchKey="Strong") -> StrongNode (edge branch="Strong")
        //                                       -> ModerateNode (edge branch="Moderate")
        //                                       -> WeakNode (edge branch="Weak")
        // Only StrongNode should execute; the other two must be pruned dead, same as
        // IF's boolean branching, but compared as strings via branchKey.
        WorkflowExecutor executor = new WorkflowExecutor(new NodeStrategyRegistry(
                List.of(triggerStrategy("webhook"), fakeSwitch(),
                        echoStrategy("strongNode"), echoStrategy("moderateNode"), echoStrategy("weakNode"))));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("Trigger", "webhook"));
        ObjectNode switchNode = node("Switch", "switch");
        switchNode.putObject("data").put("branchKey", "Strong");
        nodes.add(switchNode);
        nodes.add(node("StrongNode", "strongNode"));
        nodes.add(node("ModerateNode", "moderateNode"));
        nodes.add(node("WeakNode", "weakNode"));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("Trigger", "Switch", null));
        edges.add(edge("Switch", "StrongNode", "Strong"));
        edges.add(edge("Switch", "ModerateNode", "Moderate"));
        edges.add(edge("Switch", "WeakNode", "Weak"));

        WorkflowExecutor.ExecutionRunResult result = executor.run(USER_ID, WORKFLOW_ID, EXECUTION_ID, nodes, edges,
                JsonUtils.mapper().createObjectNode().put("match", "Strong"));

        assertThat(result.success()).isTrue();
        List<String> executedNodeIds = result.steps().stream().map(LogStep::getNodeId).toList();
        assertThat(executedNodeIds).contains("Trigger", "Switch", "StrongNode");
        assertThat(executedNodeIds).doesNotContain("ModerateNode", "WeakNode");
    }
}
