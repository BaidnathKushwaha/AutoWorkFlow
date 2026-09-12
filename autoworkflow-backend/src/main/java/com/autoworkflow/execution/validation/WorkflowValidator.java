package com.autoworkflow.execution.validation;

import com.autoworkflow.common.exception.WorkflowException;
import com.autoworkflow.execution.condition.ConditionEvaluator;
import com.autoworkflow.execution.engine.NodeStrategyRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.autoworkflow.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** Validates workflow structure and deterministic advanced-control-flow configuration. */
@Component
@RequiredArgsConstructor
public class WorkflowValidator {
    private final NodeStrategyRegistry registry;
    private final ConditionEvaluator conditionEvaluator;

    public WorkflowValidationResult validate(JsonNode nodes, JsonNode edges) { return validateForDeployment(nodes, edges); }
    public WorkflowValidationResult validate(Workflow workflow) { return validateForDeployment(workflow); }
    public WorkflowValidationResult validateForExecution(JsonNode nodes, JsonNode edges) { return validateInternal(nodes, edges, false); }
    public WorkflowValidationResult validateForExecution(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForExecution(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }
    public WorkflowValidationResult validateForDeployment(JsonNode nodes, JsonNode edges) { return validateInternal(nodes, edges, true); }
    public WorkflowValidationResult validateForDeployment(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForDeployment(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }
    public void validateOrThrow(JsonNode nodes, JsonNode edges) { validateDeploymentOrThrow(nodes, edges); }
    public void validateOrThrow(Workflow workflow) { validateDeploymentOrThrow(workflow); }
    public void validateExecutionOrThrow(JsonNode nodes, JsonNode edges) { throwIfInvalid(validateForExecution(nodes, edges)); }
    public void validateExecutionOrThrow(Workflow workflow) { if (workflow != null) validateExecutionOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges()); }
    public void validateDeploymentOrThrow(JsonNode nodes, JsonNode edges) { throwIfInvalid(validateForDeployment(nodes, edges)); }
    public void validateDeploymentOrThrow(Workflow workflow) { if (workflow != null) validateDeploymentOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges()); }
    private void throwIfInvalid(WorkflowValidationResult result) { if (!result.isValid()) throw new WorkflowException(result.error()); }

    private WorkflowValidationResult validateInternal(JsonNode canvasNodes, JsonNode canvasEdges, boolean requireTrigger) {
        if (canvasNodes == null || !canvasNodes.isArray() || canvasNodes.isEmpty()) return WorkflowValidationResult.invalid("Workflow contains no nodes.");
        Set<String> ids = new HashSet<>();
        Map<String, JsonNode> byId = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        boolean hasTrigger = false;

        for (JsonNode node : canvasNodes) {
            String id = node.path("id").asText("").trim();
            if (id.isBlank()) return WorkflowValidationResult.invalid("Workflow contains a node with a missing or blank ID.");
            if (!ids.add(id)) return WorkflowValidationResult.invalid("Duplicate node ID detected: '" + id + "'.");
            String type = node.path("type").asText("").trim();
            if (type.isBlank()) return WorkflowValidationResult.invalid("Node '" + id + "' has a missing or blank node type.");
            if (!registry.isRegisteredType(type)) return WorkflowValidationResult.invalid("Unknown or unregistered node type: '" + type + "' for node '" + id + "'.");
            byId.put(id, node); types.put(id, type);
            if (registry.isTriggerType(type)) hasTrigger = true;
            WorkflowValidationResult check = validateNodeConfig(id, type, node.path("data"), ids);
            if (!check.isValid()) return check;
        }
        if (requireTrigger && !hasTrigger) return WorkflowValidationResult.invalid("Workflow has no trigger node (e.g. Webhook, Cron, GitHub Event, or Email Received).");

        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, List<JsonNode>> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        for (String id : ids) incoming.put(id, 0);
        if (canvasEdges != null && canvasEdges.isArray()) {
            for (JsonNode edge : canvasEdges) {
                String source = edge.path("source").asText("").trim();
                String target = edge.path("target").asText("").trim();
                if (source.isBlank() || target.isBlank()) return WorkflowValidationResult.invalid("Workflow contains an edge with a missing or blank source/target.");
                if (!ids.contains(source) || !ids.contains(target)) return WorkflowValidationResult.invalid("Edge references a node that does not exist: '" + source + "' -> '" + target + "'.");
                String branch = edge.path("data").path("branch").asText("");
                String role = edge.path("data").path("loopRole").asText("");
                String edgeKey = source + "->" + target + ":" + branch + ":" + role;
                if (!edgeKeys.add(edgeKey)) return WorkflowValidationResult.invalid("Duplicate edge detected from '" + source + "' to '" + target + "'.");
                graph.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                outgoing.computeIfAbsent(source, k -> new ArrayList<>()).add(edge);
                incoming.merge(target, 1, Integer::sum);
            }
        }

        List<String> cycle = detectCyclePath(ids, graph);
        if (cycle != null) return WorkflowValidationResult.invalid("Workflow contains a cycle: " + String.join(" -> ", cycle) + ". Loop bodies must not re-enter the Loop node.");

        WorkflowValidationResult advanced = validateAdvancedControlFlow(byId, types, incoming, outgoing);
        return advanced.isValid() ? WorkflowValidationResult.valid() : advanced;
    }

    private WorkflowValidationResult validateNodeConfig(String id, String type, JsonNode data, Set<String> ids) {
        if ("summarizer".equalsIgnoreCase(type) && data.has("maxLength")) {
            try { if (Integer.parseInt(data.get("maxLength").asText()) <= 0) return WorkflowValidationResult.invalid("Node '" + id + "' (Summarizer) has invalid maxLength."); }
            catch (NumberFormatException e) { return WorkflowValidationResult.invalid("Node '" + id + "' (Summarizer) has non-numeric maxLength."); }
        }
        if ("if_condition".equalsIgnoreCase(type)) {
            try { conditionEvaluator.validate(data.has("condition") ? data.get("condition") : data); }
            catch (RuntimeException e) { return WorkflowValidationResult.invalid("Node '" + id + "' (IF): " + e.getMessage()); }
        }
        if ("loop".equalsIgnoreCase(type)) {
            String array = data.path("arrayField").asText("items").trim();
            String body = data.path("bodyStartNodeId").asText("").trim();
            String continuation = data.path("continuationNodeId").asText("").trim();
            if (array.isBlank()) return WorkflowValidationResult.invalid("Node '" + id + "' (Loop) requires arrayField.");
            if (body.isBlank() || continuation.isBlank()) return WorkflowValidationResult.invalid("Node '" + id + "' (Loop) requires bodyStartNodeId and continuationNodeId.");
            if (body.equals(continuation)) return WorkflowValidationResult.invalid("Node '" + id + "' (Loop) cannot use the same node for body and continuation.");
            if (!ids.contains(body)) return WorkflowValidationResult.invalid("Node '" + id + "' (Loop) bodyStartNodeId '" + body + "' does not exist.");
            if (!ids.contains(continuation)) return WorkflowValidationResult.invalid("Node '" + id + "' (Loop) continuationNodeId '" + continuation + "' does not exist.");
        }
        if ("switch".equalsIgnoreCase(type)) {
            JsonNode cases = data.path("cases");
            String def = data.path("defaultCase").asText("").trim();
            if (!cases.isArray() || cases.isEmpty()) return WorkflowValidationResult.invalid("Node '" + id + "' (Switch) requires at least one case.");
            if (def.isBlank()) return WorkflowValidationResult.invalid("Node '" + id + "' (Switch) requires a defaultCase.");
            boolean found = false; for (JsonNode c : cases) if (def.equals(c.asText().trim())) found = true;
            if (!found) return WorkflowValidationResult.invalid("Node '" + id + "' (Switch) defaultCase must be one of cases.");
        }
        return WorkflowValidationResult.valid();
    }

    private WorkflowValidationResult validateAdvancedControlFlow(Map<String, JsonNode> byId, Map<String, String> types,
                                                                  Map<String, Integer> incoming, Map<String, List<JsonNode>> outgoing) {
        for (Map.Entry<String, String> entry : types.entrySet()) {
            String id = entry.getKey(); String type = entry.getValue(); JsonNode data = byId.get(id).path("data");
            List<JsonNode> outs = outgoing.getOrDefault(id, List.of());
            if ("loop".equals(type)) {
                String body = data.path("bodyStartNodeId").asText("").trim();
                String continuation = data.path("continuationNodeId").asText("").trim();
                long bodyEdges = outs.stream().filter(e -> body.equals(e.path("target").asText()) && ("body".equals(e.path("data").path("loopRole").asText()) || "body".equals(e.path("data").path("branch").asText()))).count();
                long continuationEdges = outs.stream().filter(e -> continuation.equals(e.path("target").asText()) && ("continuation".equals(e.path("data").path("loopRole").asText()) || "continuation".equals(e.path("data").path("branch").asText()))).count();
                if (bodyEdges != 1) return WorkflowValidationResult.invalid("Loop node '" + id + "' must have exactly one body edge to bodyStartNodeId.");
                if (continuationEdges != 1) return WorkflowValidationResult.invalid("Loop node '" + id + "' must have exactly one continuation edge to continuationNodeId.");
                // Body nodes may not have an arbitrary edge into the outside graph. The only
                // permitted body exit is the configured continuation target, marked loopReturn.
                Set<String> bodyNodes = new HashSet<>();
                Deque<String> q = new ArrayDeque<>(); q.add(body);
                while (!q.isEmpty()) {
                    String current = q.removeFirst(); if (!bodyNodes.add(current)) continue;
                    for (JsonNode edge : outgoing.getOrDefault(current, List.of())) {
                        String target = edge.path("target").asText();
                        if (target.equals(continuation)) {
                            if (!edge.path("data").path("loopReturn").asBoolean(false)) return WorkflowValidationResult.invalid("Loop body exit '" + current + " -> " + continuation + "' must be marked loopReturn=true.");
                        } else if (!target.equals(id)) {
                            q.addLast(target);
                        } else {
                            return WorkflowValidationResult.invalid("Loop body cannot point back to Loop node '" + id + "'.");
                        }
                    }
                }
                if (bodyNodes.contains(continuation)) return WorkflowValidationResult.invalid("Loop continuation cannot be inside its body.");
                // Any incoming edge to the body other than the Loop controller creates an
                // ambiguous external producer for an iteration-scoped start.
                for (String bodyNode : bodyNodes) {
                    if (bodyNode.equals(body)) continue;
                    if (incoming.getOrDefault(bodyNode, 0) == 0) continue;
                }
            }
            if ("merge".equals(type) && incoming.getOrDefault(id, 0) < 1) return WorkflowValidationResult.invalid("Merge node '" + id + "' is disconnected.");
        }
        return WorkflowValidationResult.valid();
    }

    private List<String> detectCyclePath(Set<String> ids, Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>(), stack = new HashSet<>();
        for (String id : ids) { List<String> path = new ArrayList<>(); if (dfs(id, graph, visited, stack, path)) return path; }
        return null;
    }
    private boolean dfs(String curr, Map<String, List<String>> graph, Set<String> visited, Set<String> stack, List<String> path) {
        if (stack.contains(curr)) { path.add(curr); return true; }
        if (visited.contains(curr)) return false;
        visited.add(curr); stack.add(curr); path.add(curr);
        for (String next : graph.getOrDefault(curr, List.of())) if (dfs(next, graph, visited, stack, path)) return true;
        stack.remove(curr); path.remove(path.size() - 1); return false;
    }
}
