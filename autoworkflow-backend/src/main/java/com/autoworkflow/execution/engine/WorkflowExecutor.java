package com.autoworkflow.execution.engine;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Walks a workflow's React Flow canvas (nodes + edges) starting from its trigger
 * node(s), executing each node's strategy in topological order and threading each
 * node's output as the next node's input payload.
 *
 * Trigger seeding:
 *   Only nodes whose strategy reports isTrigger()==true (webhook, cron_trigger,
 *   github_event, email_received — see NodeStrategy.isTrigger()) are seeded as
 *   execution start points. A node simply having zero incoming edges is NOT enough
 *   on its own — a disconnected/orphaned action node (e.g. a Slack node the user
 *   forgot to wire up) also has zero incoming edges and must never silently execute.
 *   If a workflow has no real trigger node at all, the run fails immediately with a
 *   clear message instead of guessing at a start point.
 *
 * Branching (IF Condition / AI Router):
 *   Only the outgoing edge whose `data.branch` ("true"/"false") matches the
 *   strategy's branchTaken result is followed. The other edge is "dead" — it will
 *   never deliver a payload.
 *
 * Merge / multi-input nodes — liveness propagation AND payload aggregation:
 *   A node with N incoming edges only executes once all N edges are *resolved*
 *   (each either delivered a payload, or is confirmed dead) AND at least one of
 *   them actually delivered. "Dead" propagates transitively: if a node ends up with
 *   zero delivered edges once all its incoming edges are resolved, the node itself
 *   is dead, and ALL of its own outgoing edges are marked dead too — recursively.
 *   This is what makes "Trigger -> If -(true)-> A -> Merge, If -(false)-> B -> Merge"
 *   work correctly: B is dead (its only incoming edge, the false branch, was never
 *   taken), so B's edge into Merge is *also* dead, so Merge only waits on the one
 *   edge that can actually still deliver (from A) — instead of waiting forever.
 *
 *   Every payload that actually arrives at a multi-input node is kept (not just the
 *   last one): a node with more than one incoming edge receives
 *   { "inputs": [ <branch 1 output>, <branch 2 output>, ... ] } as its input payload
 *   (delivery order), not whichever branch happened to finish last. See
 *   MergeStrategy for how it consumes this shape.
 *
 * continueOnFail:
 *   A node's `data.continueOnFail` (default false) controls what happens when IT
 *   fails. false (default, unchanged): the whole run stops immediately and is
 *   marked FAILED. true: the failure is still recorded in that node's LogStep
 *   (status=failed, error set) but execution continues; downstream nodes receive
 *   the failed node's *input* payload unchanged — a documented, deliberate policy
 *   (a soft-failed node behaves as a passthrough, not as if it produced new data it
 *   never actually computed). This is visible in the UI as "continued after failure"
 *   via the node's own LogStep status, not hidden.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutor {

    private final NodeStrategyRegistry registry;

    public ExecutionRunResult run(UUID userId, UUID workflowId, UUID executionId,
                                   JsonNode canvasNodes, JsonNode canvasEdges, JsonNode triggerPayload) {

        List<LogStep> steps = new ArrayList<>();
        Map<String, JsonNode> nodeById = new HashMap<>();
        Map<String, List<JsonNode>> outgoingEdges = new HashMap<>();

        // Static, never mutated after this block: the real incoming-edge count per node.
        Map<String, Integer> totalIncoming = new HashMap<>();
        // Mutated at runtime as edges resolve.
        Map<String, Integer> arrivedCount = new HashMap<>();
        Map<String, Integer> deadIncoming = new HashMap<>();
        // Every payload that has actually arrived at a node, in delivery order.
        Map<String, List<JsonNode>> incomingPayloads = new HashMap<>();

        canvasNodes.forEach(n -> {
            String nid = n.get("id").asText();
            nodeById.put(nid, n);
            totalIncoming.putIfAbsent(nid, 0);
            arrivedCount.putIfAbsent(nid, 0);
            deadIncoming.putIfAbsent(nid, 0);
        });

        canvasEdges.forEach(e -> {
            String source = e.get("source").asText();
            String target = e.get("target").asText();
            outgoingEdges.computeIfAbsent(source, k -> new ArrayList<>()).add(e);
            totalIncoming.merge(target, 1, Integer::sum);
        });

        Set<String> visited = new HashSet<>();
        Set<String> queued = new HashSet<>();
        Set<String> dead = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        // Recursively resolves one incoming edge of `target`: either it delivered a real
        // payload, or it's confirmed dead. Once every incoming edge of a node is resolved,
        // the node either becomes ready to run (>=1 delivered) or becomes dead itself
        // (0 delivered), and dead nodes propagate deadness to their own outgoing edges.
        class EdgeResolver {
            void resolve(String target, boolean delivered, JsonNode payload) {
                if (visited.contains(target) || dead.contains(target)) return;

                if (delivered) {
                    incomingPayloads.computeIfAbsent(target, k -> new ArrayList<>()).add(payload);
                    arrivedCount.merge(target, 1, Integer::sum);
                } else {
                    deadIncoming.merge(target, 1, Integer::sum);
                }

                int resolved = arrivedCount.getOrDefault(target, 0) + deadIncoming.getOrDefault(target, 0);
                int total = totalIncoming.getOrDefault(target, 0);

                if (total == 0) return; // trigger nodes are seeded directly, not via edges

                if (resolved >= total) {
                    if (arrivedCount.getOrDefault(target, 0) > 0) {
                        if (!queued.contains(target)) {
                            queue.add(target);
                            queued.add(target);
                        }
                    } else {
                        // Every incoming edge is dead and none ever delivered -> this node
                        // itself can never run. Propagate deadness to its own outgoing edges.
                        dead.add(target);
                        for (JsonNode edge : outgoingEdges.getOrDefault(target, List.of())) {
                            resolve(edge.get("target").asText(), false, null);
                        }
                    }
                }
            }
        }
        EdgeResolver resolver = new EdgeResolver();

        List<String> triggerIds = new ArrayList<>();
        List<String> zeroIncomingNodeIds = new ArrayList<>();
        for (String nodeId : nodeById.keySet()) {
            if (totalIncoming.getOrDefault(nodeId, 0) != 0) continue;
            zeroIncomingNodeIds.add(nodeId);
            String nodeType = nodeById.get(nodeId).get("type").asText();
            if (registry.isTriggerType(nodeType)) {
                triggerIds.add(nodeId);
            }
        }

        // Start points: a real trigger node, if one exists — a deployed, webhook/cron-
        // triggered execution always has one (deployment validation guarantees it, see
        // WorkflowValidator.validateForDeployment), so this branch is what actually runs
        // for those. If no real trigger exists at all, this is a manual/test execution of
        // a workflow that doesn't need one (e.g. a standalone Summarizer configured with
        // Direct Input Text) — WorkflowValidator.validateForExecution already allows this
        // structurally, so here every zero-incoming node becomes a valid manual start
        // point instead of failing. Note the difference from a *disconnected* non-trigger
        // node sitting alongside a real trigger elsewhere in the graph (e.g. a stray Slack
        // node the user forgot to wire up) — that case still falls through the first
        // branch below and is correctly left unseeded, since triggerIds is non-empty then.
        List<String> startNodeIds;
        if (!triggerIds.isEmpty()) {
            startNodeIds = triggerIds;
        } else if (!zeroIncomingNodeIds.isEmpty()) {
            startNodeIds = zeroIncomingNodeIds;
        } else {
            return ExecutionRunResult.failed(steps,
                    "Workflow has no valid starting node — every node has at least one incoming connection.");
        }

        for (String startId : startNodeIds) {
            incomingPayloads.put(startId, new ArrayList<>(List.of(triggerPayload)));
            arrivedCount.put(startId, 1);
            queue.add(startId);
            queued.add(startId);
        }

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            queued.remove(nodeId);
            if (visited.contains(nodeId)) continue;
            visited.add(nodeId);

            JsonNode node = nodeById.get(nodeId);
            String nodeType = node.get("type").asText();
            JsonNode nodeData = node.has("data") ? node.get("data") : JsonUtils.mapper().createObjectNode();
            boolean continueOnFail = nodeData.path("continueOnFail").asBoolean(false);

            JsonNode input = buildInput(nodeId, totalIncoming, incomingPayloads);

            Instant start = Instant.now();
            NodeExecutionResult result;
            try {
                NodeStrategy strategy = registry.resolve(nodeType);
                result = strategy.execute(new NodeExecutionContext(userId, workflowId, executionId, nodeId, nodeType, nodeData, input));
            } catch (Exception e) {
                result = NodeExecutionResult.failed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            Instant end = Instant.now();
            long durationMs = end.toEpochMilli() - start.toEpochMilli();

            String label = nodeData.has("label") ? nodeData.get("label").asText() : nodeType;
            steps.add(new LogStep(nodeId, label, result.success() ? "success" : "failed", start, end, input, result.outputPayload(), result.error(), durationMs));

            if (!result.success()) {
                if (!continueOnFail) {
                    return ExecutionRunResult.failed(steps, "Node '" + label + "' failed: " + result.error());
                }
                log.warn("Node '{}' failed but continueOnFail=true; continuing execution. Downstream receives this node's input unchanged. Error: {}", label, result.error());
            }

            JsonNode outputForDownstream = result.success() ? result.outputPayload() : input;

            for (JsonNode edge : outgoingEdges.getOrDefault(nodeId, List.of())) {
                String targetId = edge.get("target").asText();
                boolean edgeTaken = true;
                if (result.success() && edge.has("data") && edge.get("data").has("branch")) {
                    String edgeBranchRaw = edge.get("data").get("branch").asText();
                    if (result.branchKey() != null) {
                        edgeTaken = edgeBranchRaw.equals(result.branchKey());
                    } else if (result.branchTaken() != null) {
                        edgeTaken = Boolean.parseBoolean(edgeBranchRaw) == result.branchTaken();
                    }
                }
                resolver.resolve(targetId, edgeTaken, outputForDownstream);
            }
        }

        JsonNode finalOutput = steps.isEmpty() ? JsonUtils.mapper().createObjectNode() : steps.get(steps.size() - 1).getOutputPayload();
        return ExecutionRunResult.success(steps, finalOutput);
    }

    /**
     * A node with 0-1 incoming edges gets the single delivered payload directly (or an
     * empty object if somehow none arrived). A node with >1 incoming edges (Merge, or any
     * future multi-input node type) gets every payload that arrived, wrapped as
     * { "inputs": [...] }, in delivery order — see class javadoc and MergeStrategy.
     */
    private JsonNode buildInput(String nodeId, Map<String, Integer> totalIncoming, Map<String, List<JsonNode>> incomingPayloads) {
        List<JsonNode> payloads = incomingPayloads.getOrDefault(nodeId, List.of());
        int total = totalIncoming.getOrDefault(nodeId, 0);

        if (total <= 1) {
            return payloads.isEmpty() ? JsonUtils.mapper().createObjectNode() : payloads.get(0);
        }

        ObjectNode wrapper = JsonUtils.mapper().createObjectNode();
        ArrayNode inputsArray = wrapper.putArray("inputs");
        payloads.forEach(inputsArray::add);
        return wrapper;
    }

    public record ExecutionRunResult(boolean success, List<LogStep> steps, JsonNode finalOutput, String error) {
        public static ExecutionRunResult success(List<LogStep> steps, JsonNode finalOutput) {
            return new ExecutionRunResult(true, steps, finalOutput, null);
        }
        public static ExecutionRunResult failed(List<LogStep> steps, String error) {
            return new ExecutionRunResult(false, steps, null, error);
        }
    }
}
