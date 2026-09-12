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

/** Deterministic graph orchestrator with scoped Loop execution. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutor {
    private final NodeStrategyRegistry registry;

    public ExecutionRunResult run(UUID userId, UUID workflowId, UUID executionId, JsonNode canvasNodes, JsonNode canvasEdges, JsonNode triggerPayload) {
        List<JsonNode> nodes = asList(canvasNodes); List<JsonNode> edges = asList(canvasEdges); Set<String> triggerIds = new LinkedHashSet<>();
        for (JsonNode node : nodes) if (registry.isTriggerType(node.path("type").asText(""))) triggerIds.add(node.path("id").asText());
        List<String> starts = new ArrayList<>(); Map<String, Integer> incoming = incomingCounts(nodes, edges);
        for (JsonNode node : nodes) { String id = node.path("id").asText(); boolean validStart = incoming.getOrDefault(id, 0) == 0; if (!triggerIds.isEmpty()) validStart = validStart && triggerIds.contains(id); if (validStart) starts.add(id); }
        if (starts.isEmpty()) return ExecutionRunResult.failed(List.of(), "Workflow has no valid starting node: every node has an incoming connection.");
        JsonNode seed = triggerPayload == null ? JsonUtils.mapper().createObjectNode() : triggerPayload;
        ScopeResult result = executeScope(userId, workflowId, executionId, nodes, edges, starts, Collections.emptyMap(), seed, null, null, null, null, null, Collections.emptySet());
        if (!result.success()) return ExecutionRunResult.failed(result.steps(), result.error()); return ExecutionRunResult.success(result.steps(), result.finalOutput());
    }

    private ScopeResult executeScope(UUID userId, UUID workflowId, UUID executionId, List<JsonNode> nodes, List<JsonNode> edges, List<String> startIds, Map<String, JsonNode> seededInputs, JsonNode defaultSeed, Integer iterationIndex, Integer iterationCount, String iterationId, String parentLoopNodeId, String branchPath, Set<String> stopBefore) {
        Map<String, JsonNode> nodeById = new HashMap<>(); Map<String, List<JsonNode>> outgoing = new HashMap<>(); Map<String, Integer> totalIncoming = new HashMap<>(); Map<String, Integer> arrived = new HashMap<>(); Map<String, Integer> deadIncoming = new HashMap<>(); Map<String, List<JsonNode>> payloads = new HashMap<>();
        for (JsonNode n : nodes) { String id = n.path("id").asText(); nodeById.put(id, n); totalIncoming.put(id, 0); arrived.put(id, 0); deadIncoming.put(id, 0); }
        for (JsonNode e : edges) { String source = e.path("source").asText(), target = e.path("target").asText(); if (!nodeById.containsKey(source) || !nodeById.containsKey(target)) continue; outgoing.computeIfAbsent(source, k -> new ArrayList<>()).add(e); totalIncoming.merge(target, 1, Integer::sum); }
        Set<String> visited = new HashSet<>(), queued = new HashSet<>(), dead = new HashSet<>(); Deque<String> queue = new ArrayDeque<>(); List<LogStep> steps = new ArrayList<>(); JsonNode initialOutput = defaultSeed == null ? JsonUtils.mapper().createObjectNode() : defaultSeed; final JsonNode[] lastOutput = {initialOutput};
        class Resolver { void resolve(String target, boolean delivered, JsonNode payload) { if (stopBefore.contains(target) || visited.contains(target) || dead.contains(target)) return; if (delivered) { payloads.computeIfAbsent(target, k -> new ArrayList<>()).add(payload); arrived.merge(target, 1, Integer::sum); } else deadIncoming.merge(target, 1, Integer::sum); int resolved = arrived.getOrDefault(target, 0) + deadIncoming.getOrDefault(target, 0), total = totalIncoming.getOrDefault(target, 0); if (total == 0) return; if (resolved >= total) { if (arrived.getOrDefault(target, 0) > 0) { if (queued.add(target)) queue.add(target); } else { dead.add(target); for (JsonNode e : outgoing.getOrDefault(target, Collections.emptyList())) resolve(e.path("target").asText(), false, null); } } } }
        Resolver resolver = new Resolver();
        for (String startId : startIds) { if (!nodeById.containsKey(startId) || stopBefore.contains(startId)) continue; JsonNode startSeed = seededInputs.get(startId); if (startSeed == null) startSeed = defaultSeed; if (startSeed == null) startSeed = JsonUtils.mapper().createObjectNode(); payloads.put(startId, new ArrayList<>(List.of(startSeed))); arrived.put(startId, 1); queue.add(startId); queued.add(startId); }
        while (!queue.isEmpty()) {
            String nodeId = queue.poll(); queued.remove(nodeId); if (visited.contains(nodeId) || stopBefore.contains(nodeId)) continue; visited.add(nodeId); JsonNode node = nodeById.get(nodeId); String nodeType = node.path("type").asText(); JsonNode config = node.get("data"); if (config == null) config = JsonUtils.mapper().createObjectNode();
            JsonNode input = seededInputs.containsKey(nodeId) ? seededInputs.get(nodeId) : buildInput(nodeId, totalIncoming, payloads);
            Instant start = Instant.now(); NodeExecutionResult result;
            try { result = registry.resolve(nodeType).execute(new NodeExecutionContext(userId, workflowId, executionId, nodeId, nodeType, config, input, iterationIndex, iterationCount, iterationId, parentLoopNodeId, branchPath)); }
            catch (Exception e) { String message = e.getMessage(); if (message == null || message.isBlank()) message = e.getClass().getSimpleName(); result = NodeExecutionResult.failed(message); }
            Instant end = Instant.now(); String label = config.path("label").asText(nodeType); long duration = end.toEpochMilli() - start.toEpochMilli(); steps.add(new LogStep(nodeId, label, result.success() ? "success" : "failed", start, end, input, result.outputPayload(), result.error(), duration, iterationIndex, iterationCount, iterationId, parentLoopNodeId, branchPath));
            if (!result.success()) { boolean continueOnFail = config.path("continueOnFail").asBoolean(false); if (!continueOnFail) return ScopeResult.failed(steps, "Node '" + label + "' failed: " + result.error()); log.warn("Node '{}' failed but continueOnFail=true; continuing.", label); }
            JsonNode output = result.outputPayload() == null ? input : result.outputPayload(); lastOutput[0] = output;
            if ("loop".equals(nodeType) && result.success()) { ScopeResult loopResult = executeLoop(userId, workflowId, executionId, nodes, edges, nodeId, config, output, steps, iterationIndex, iterationCount, iterationId, parentLoopNodeId, branchPath); if (!loopResult.success()) return loopResult; steps = new ArrayList<>(loopResult.steps()); lastOutput[0] = loopResult.finalOutput(); continue; }
            for (JsonNode edge : outgoing.getOrDefault(nodeId, Collections.emptyList())) { String target = edge.path("target").asText(); String edgeBranch = edge.path("data").path("branch").asText(""); boolean taken = true; String branchKey = result.branchKey(); Boolean branchTaken = result.branchTaken(); if (result.success() && branchKey != null) taken = branchKey.equals(edgeBranch); else if (result.success() && branchTaken != null && !edgeBranch.isBlank()) taken = Boolean.parseBoolean(edgeBranch) == branchTaken.booleanValue(); if (!stopBefore.contains(target)) resolver.resolve(target, taken, output); }
        }
        return ScopeResult.success(steps, lastOutput[0]);
    }

    private ScopeResult executeLoop(UUID userId, UUID workflowId, UUID executionId, List<JsonNode> nodes, List<JsonNode> edges, String loopNodeId, JsonNode config, JsonNode loopOutput, List<LogStep> accumulated, Integer outerIterationIndex, Integer outerIterationCount, String outerIterationId, String outerParentLoop, String outerBranchPath) {
        JsonNode itemsNode = loopOutput.path("items"); if (!itemsNode.isArray()) return ScopeResult.failed(accumulated, "Loop node did not produce an array of items.");
        String bodyStart = config.path("bodyStartNodeId").asText("").trim(), continuation = config.path("continuationNodeId").asText("").trim();
        if (itemsNode.size() > 0 && (bodyStart.isEmpty() || continuation.isEmpty())) return ScopeResult.failed(accumulated, "Loop requires 'bodyStartNodeId' and 'continuationNodeId' when executing a non-empty collection.");
        if (!bodyStart.isEmpty() && nodes.stream().noneMatch(n -> bodyStart.equals(n.path("id").asText()))) return ScopeResult.failed(accumulated, "Loop bodyStartNodeId '" + bodyStart + "' does not exist.");
        if (!continuation.isEmpty() && nodes.stream().noneMatch(n -> continuation.equals(n.path("id").asText()))) return ScopeResult.failed(accumulated, "Loop continuationNodeId '" + continuation + "' does not exist.");
        ArrayNode results = JsonUtils.mapper().createArrayNode();
        for (int i = 0; i < itemsNode.size(); i++) { JsonNode item = itemsNode.get(i); ObjectNode iterationInput = JsonUtils.mapper().createObjectNode(); iterationInput.set("item", item.deepCopy()); iterationInput.put("index", i); iterationInput.put("count", itemsNode.size()); String iterationId = executionId + ":" + loopNodeId + ":" + i; String scopedBranch = appendBranch(outerBranchPath, "loop[" + i + "]"); ScopeResult body = executeScope(userId, workflowId, executionId, nodes, edges, Collections.singletonList(bodyStart), Collections.emptyMap(), iterationInput, i, itemsNode.size(), iterationId, loopNodeId, scopedBranch, Collections.singleton(continuation)); accumulated = new ArrayList<>(accumulated); accumulated.addAll(body.steps()); if (!body.success()) return ScopeResult.failed(accumulated, body.error()); JsonNode bodyOutput = body.finalOutput() == null ? JsonUtils.mapper().nullNode() : body.finalOutput(); results.add(bodyOutput); }
        ObjectNode collected = JsonUtils.mapper().createObjectNode(); collected.set("items", itemsNode.deepCopy()); collected.put("count", itemsNode.size()); collected.set("results", results);
        if (!continuation.isEmpty()) { Map<String, JsonNode> continuationSeed = Collections.singletonMap(continuation, collected); ScopeResult continuationResult = executeScope(userId, workflowId, executionId, nodes, edges, Collections.singletonList(continuation), continuationSeed, collected, outerIterationIndex, outerIterationCount, outerIterationId, outerParentLoop, appendBranch(outerBranchPath, "loop.continuation"), Collections.emptySet()); accumulated = new ArrayList<>(accumulated); accumulated.addAll(continuationResult.steps()); if (!continuationResult.success()) return ScopeResult.failed(accumulated, continuationResult.error()); return ScopeResult.success(accumulated, continuationResult.finalOutput()); }
        return ScopeResult.success(accumulated, collected);
    }

    private String appendBranch(String current, String next) { if (current == null || current.isBlank()) return next; return current + " > " + next; }
    private JsonNode buildInput(String nodeId, Map<String, Integer> totalIncoming, Map<String, List<JsonNode>> payloads) { List<JsonNode> values = payloads.getOrDefault(nodeId, Collections.emptyList()); if (totalIncoming.getOrDefault(nodeId, 0) <= 1) return values.isEmpty() ? JsonUtils.mapper().createObjectNode() : values.get(0); ObjectNode wrapper = JsonUtils.mapper().createObjectNode(); ArrayNode inputs = wrapper.putArray("inputs"); for (JsonNode value : values) inputs.add(value); return wrapper; }
    private Map<String, Integer> incomingCounts(List<JsonNode> nodes, List<JsonNode> edges) { Map<String, Integer> counts = new HashMap<>(); for (JsonNode n : nodes) counts.put(n.path("id").asText(), 0); for (JsonNode e : edges) counts.merge(e.path("target").asText(), 1, Integer::sum); return counts; }
    private List<JsonNode> asList(JsonNode node) { List<JsonNode> out = new ArrayList<>(); if (node != null && node.isArray()) node.forEach(out::add); return out; }
    private record ScopeResult(boolean success, List<LogStep> steps, JsonNode finalOutput, String error) { static ScopeResult success(List<LogStep> steps, JsonNode output) { return new ScopeResult(true, steps, output, null); } static ScopeResult failed(List<LogStep> steps, String error) { return new ScopeResult(false, steps, null, error); } }
    public record ExecutionRunResult(boolean success, List<LogStep> steps, JsonNode finalOutput, String error) { public static ExecutionRunResult success(List<LogStep> steps, JsonNode finalOutput) { return new ExecutionRunResult(true, steps, finalOutput, null); } public static ExecutionRunResult failed(List<LogStep> steps, String error) { return new ExecutionRunResult(false, steps, null, error); } }
}
