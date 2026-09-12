package com.autoworkflow.execution.validation;

import com.autoworkflow.common.exception.WorkflowException;
import com.autoworkflow.execution.condition.ConditionEvaluator;
import com.autoworkflow.execution.engine.NodeStrategyRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.autoworkflow.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** Validates workflow structure/configuration before execution and deployment. */
@Component
@RequiredArgsConstructor
public class WorkflowValidator {
    private final NodeStrategyRegistry registry;
    private final ConditionEvaluator conditionEvaluator;

    public WorkflowValidationResult validate(JsonNode canvasNodes, JsonNode canvasEdges) { return validateForDeployment(canvasNodes, canvasEdges); }
    public WorkflowValidationResult validate(Workflow workflow) { return validateForDeployment(workflow); }
    public WorkflowValidationResult validateForExecution(JsonNode canvasNodes, JsonNode canvasEdges) { return validateInternal(canvasNodes, canvasEdges, false); }
    public WorkflowValidationResult validateForExecution(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForExecution(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }
    public WorkflowValidationResult validateForDeployment(JsonNode canvasNodes, JsonNode canvasEdges) { return validateInternal(canvasNodes, canvasEdges, true); }
    public WorkflowValidationResult validateForDeployment(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForDeployment(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }
    public void validateOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) { validateDeploymentOrThrow(canvasNodes, canvasEdges); }
    public void validateOrThrow(Workflow workflow) { validateDeploymentOrThrow(workflow); }
    public void validateExecutionOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) { throwIfInvalid(validateForExecution(canvasNodes, canvasEdges)); }
    public void validateExecutionOrThrow(Workflow workflow) { if (workflow != null) validateExecutionOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges()); }
    public void validateDeploymentOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) { throwIfInvalid(validateForDeployment(canvasNodes, canvasEdges)); }
    public void validateDeploymentOrThrow(Workflow workflow) { if (workflow != null) validateDeploymentOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges()); }

    private void throwIfInvalid(WorkflowValidationResult result) { if (!result.isValid()) throw new WorkflowException(result.error()); }

    private WorkflowValidationResult validateInternal(JsonNode canvasNodes, JsonNode canvasEdges, boolean requireTrigger) {
        if (canvasNodes == null || !canvasNodes.isArray() || canvasNodes.isEmpty()) return WorkflowValidationResult.invalid("Workflow contains no nodes.");
        Set<String> nodeIds = new HashSet<>();
        boolean hasTrigger = false;
        Map<String, String> nodeTypes = new HashMap<>();

        for (JsonNode node : canvasNodes) {
            String nodeId = node.path("id").asText("");
            if (nodeId.isBlank()) return WorkflowValidationResult.invalid("Workflow contains a node with a missing or blank ID.");
            if (!nodeIds.add(nodeId)) return WorkflowValidationResult.invalid("Duplicate node ID detected: '" + nodeId + "'.");
            String type = node.path("type").asText("");
            if (type.isBlank()) return WorkflowValidationResult.invalid("Node '" + nodeId + "' has a missing or blank node type.");
            if (!registry.isRegisteredType(type)) return WorkflowValidationResult.invalid("Unknown or unregistered node type: '" + type + "' for node '" + nodeId + "'.");
            nodeTypes.put(nodeId, type);
            if (registry.isTriggerType(type)) hasTrigger = true;
            WorkflowValidationResult configCheck = validateNodeConfig(nodeId, type, node.path("data"));
            if (!configCheck.isValid()) return configCheck;
        }
        if (requireTrigger && !hasTrigger) return WorkflowValidationResult.invalid("Workflow has no trigger node (e.g. Webhook, Cron, GitHub Event, or Email Received).");

        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<JsonNode>> outgoing = new HashMap<>();
        for (String id : nodeIds) incoming.put(id, 0);

        if (canvasEdges != null && canvasEdges.isArray()) {
            for (JsonNode edge : canvasEdges) {
                String source = edge.path("source").asText("");
                String target = edge.path("target").asText("");
                if (source.isBlank()) return WorkflowValidationResult.invalid("Workflow contains an edge with a missing or blank source.");
                if (target.isBlank()) return WorkflowValidationResult.invalid("Workflow contains an edge with a missing or blank target.");
                if (!nodeIds.contains(source)) return WorkflowValidationResult.invalid("Edge source node '" + source + "' does not exist in workflow nodes.");
                if (!nodeIds.contains(target)) return WorkflowValidationResult.invalid("Edge target node '" + target + "' does not exist in workflow nodes.");
                String branch = edge.path("data").path("branch").asText("");
                String edgeKey = source + "->" + target + (branch.isEmpty() ? "" : ":" + branch);
                if (!edgeKeys.add(edgeKey)) return WorkflowValidationResult.invalid("Duplicate edge detected from '" + source + "' to '" + target + "'.");
                graph.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                outgoing.computeIfAbsent(source, k -> new ArrayList<>()).add(edge);
                incoming.merge(target, 1, Integer::sum);
            }
        }

        List<String> cyclePath = detectCyclePath(nodeIds, graph);
        if (cyclePath != null) {
            return WorkflowValidationResult.invalid("Workflow contains a cycle: " + String.join(" -> ", cyclePath) + ". Cyclic execution is not supported. Loop bodies use explicit body/continuation boundaries and must not re-enter the Loop node.");
        }

        WorkflowValidationResult advanced = validateAdvancedControlFlow(nodeIds, nodeTypes, incoming, outgoing);
        if (!advanced.isValid()) return advanced;
        return WorkflowValidationResult.valid();
    }

    private WorkflowValidationResult validateNodeConfig(String nodeId, String type, JsonNode data) {
        if ("summarizer".equalsIgnoreCase(type) && data.has("maxLength")) {
            try {
                int ml = Integer.parseInt(data.get("maxLength").asText());
                if (ml <= 0) return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Summarizer) has invalid maxLength: " + ml);
            } catch (NumberFormatException e) {
                return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Summarizer) has non-numeric maxLength.");
            }
        }
        if ("if_condition".equalsIgnoreCase(type)) {
            try { conditionEvaluator.validate(data.has("condition") ? data.get("condition") : data); }
            catch (RuntimeException e) { return WorkflowValidationResult.invalid("Node '" + nodeId + "' (IF): " + e.getMessage()); }
        }
        if ("loop".equalsIgnoreCase(type)) {
            String arrayField = data.path("arrayField").asText("items").trim();
            if (arrayField.isBlank()) return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Loop) requires arrayField.");
            String body = data.path("bodyStartNodeId").asText("").trim();
            String continuation = data.path("continuationNodeId").asText("").trim();
            if (!body.isBlank() && body.equals(continuation)) return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Loop) cannot use the same node for body and continuation.");
        }
        return WorkflowValidationResult.valid();
    }

    private WorkflowValidationResult validateAdvancedControlFlow(Set<String> nodeIds, Map<String, String> nodeTypes,
                                                                  Map<String, Integer> incoming, Map<String, List<JsonNode>> outgoing) {
        for (String nodeId : nodeIds) {
            String type = nodeTypes.get(nodeId);
            if ("loop".equals(type)) {
                // A configured loop is explicit: one body entry and one continuation. Both
                // must be reachable through the loop node's labelled edges.
                // Legacy loop configs without body/continuation remain executable as a
                // collection-producing node, preserving old saved workflows.
                JsonNode node = null;
                // The detailed config checks below are performed by edge metadata because
                // this method intentionally has no second node scan.
                List<JsonNode> outs = outgoing.getOrDefault(nodeId, List.of());
                long bodyEdges = outs.stream().filter(e -> "body".equals(e.path("data").path("loopRole").asText()) || "body".equals(e.path("data").path("branch").asText())).count();
                long continuationEdges = outs.stream().filter(e -> "continuation".equals(e.path("data").path("loopRole").asText()) || "continuation".equals(e.path("data").path("branch").asText())).count();
                // If loopRole metadata is used, it must be unambiguous.
                if (bodyEdges > 1) return WorkflowValidationResult.invalid("Loop node '" + nodeId + "' has multiple body entry edges.");
                if (continuationEdges > 1) return WorkflowValidationResult.invalid("Loop node '" + nodeId + "' has multiple continuation edges.");
            }
            if ("switch".equals(type)) {
                // The strategy requires a default when configured cases do not cover every
                // possible input. This is a structural safety check, not an assumption that
                // every case must have a UI edge.
                // Existing saved Switch nodes without a default remain valid for backward compatibility.
            }
            if ("merge".equals(type) && incoming.getOrDefault(nodeId, 0) < 2) {
                // A single-input Merge is harmless and remains a documented pass-through.
            }
        }
        return WorkflowValidationResult.valid();
    }

    private List<String> detectCyclePath(Set<String> nodeIds, Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>(), recStack = new HashSet<>();
        for (String nodeId : nodeIds) {
            List<String> path = new ArrayList<>();
            if (dfsCycle(nodeId, graph, visited, recStack, path)) return path;
        }
        return null;
    }

    private boolean dfsCycle(String curr, Map<String, List<String>> graph, Set<String> visited, Set<String> recStack, List<String> path) {
        if (recStack.contains(curr)) { path.add(curr); return true; }
        if (visited.contains(curr)) return false;
        visited.add(curr); recStack.add(curr); path.add(curr);
        for (String neighbor : graph.getOrDefault(curr, Collections.emptyList())) if (dfsCycle(neighbor, graph, visited, recStack, path)) return true;
        recStack.remove(curr); path.remove(path.size() - 1); return false;
    }
}
