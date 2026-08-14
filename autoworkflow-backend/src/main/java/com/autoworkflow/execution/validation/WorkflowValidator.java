package com.autoworkflow.execution.validation;

import com.autoworkflow.common.exception.WorkflowException;
import com.autoworkflow.execution.engine.NodeStrategyRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.autoworkflow.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates workflow graph structure and node configuration BEFORE execution or
 * deployment begins.
 *
 * Two validation modes, sharing one implementation (validateInternal):
 *   - validateForExecution: structural/config checks only. Used for every actual run
 *     (manual "Run", webhook, cron) — a workflow does NOT need a trigger node to be
 *     run manually (e.g. a standalone Summarizer configured with Direct Input Text).
 *   - validateForDeployment: everything execution requires, PLUS at least one trigger
 *     node. This is the gate that gives webhook/cron/etc. their guarantee: by the time
 *     an externally-triggered execution happens, a trigger node is known to exist,
 *     because deployment already checked for one. Execution-time validation re-checking
 *     that would be redundant, not incorrect — this design just avoids doing it twice.
 *
 * `validate(...)` / `validateOrThrow(...)` are kept as-is (now thin aliases for the
 * deployment-mode methods) so existing callers/tests that predate this split keep
 * working unchanged.
 */
@Component
@RequiredArgsConstructor
public class WorkflowValidator {

    private final NodeStrategyRegistry registry;

    /** Alias for validateForDeployment — kept for existing callers/tests written before the mode split. */
    public WorkflowValidationResult validate(JsonNode canvasNodes, JsonNode canvasEdges) {
        return validateForDeployment(canvasNodes, canvasEdges);
    }

    public WorkflowValidationResult validate(Workflow workflow) {
        return validateForDeployment(workflow);
    }

    /** Structural/config validation only — no trigger node required. Used for every actual execution. */
    public WorkflowValidationResult validateForExecution(JsonNode canvasNodes, JsonNode canvasEdges) {
        return validateInternal(canvasNodes, canvasEdges, false);
    }

    public WorkflowValidationResult validateForExecution(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForExecution(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }

    /** Everything validateForExecution checks, plus: the workflow must contain at least one trigger node. */
    public WorkflowValidationResult validateForDeployment(JsonNode canvasNodes, JsonNode canvasEdges) {
        return validateInternal(canvasNodes, canvasEdges, true);
    }

    public WorkflowValidationResult validateForDeployment(Workflow workflow) {
        if (workflow == null) return WorkflowValidationResult.invalid("Workflow cannot be null.");
        return validateForDeployment(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }

    /** Alias for validateDeploymentOrThrow — kept for existing callers written before the mode split. */
    public void validateOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) {
        validateDeploymentOrThrow(canvasNodes, canvasEdges);
    }

    public void validateOrThrow(Workflow workflow) {
        validateDeploymentOrThrow(workflow);
    }

    public void validateExecutionOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) {
        throwIfInvalid(validateForExecution(canvasNodes, canvasEdges));
    }

    public void validateExecutionOrThrow(Workflow workflow) {
        if (workflow == null) return;
        validateExecutionOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }

    public void validateDeploymentOrThrow(JsonNode canvasNodes, JsonNode canvasEdges) {
        throwIfInvalid(validateForDeployment(canvasNodes, canvasEdges));
    }

    public void validateDeploymentOrThrow(Workflow workflow) {
        if (workflow == null) return;
        validateDeploymentOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges());
    }

    private void throwIfInvalid(WorkflowValidationResult result) {
        if (!result.isValid()) {
            throw new WorkflowException(result.error());
        }
    }

    private WorkflowValidationResult validateInternal(JsonNode canvasNodes, JsonNode canvasEdges, boolean requireTrigger) {
        // 1. Workflow has nodes
        if (canvasNodes == null || !canvasNodes.isArray() || canvasNodes.isEmpty()) {
            return WorkflowValidationResult.invalid("Workflow contains no nodes.");
        }

        Set<String> nodeIds = new HashSet<>();
        boolean hasTrigger = false;

        // 2. Every node has a non-empty ID, 3. Unique node IDs, 4. Recognized node type
        for (JsonNode node : canvasNodes) {
            if (!node.has("id") || node.get("id").asText("").isBlank()) {
                return WorkflowValidationResult.invalid("Workflow contains a node with a missing or blank ID.");
            }
            String nodeId = node.get("id").asText();
            if (!nodeIds.add(nodeId)) {
                return WorkflowValidationResult.invalid("Duplicate node ID detected: '" + nodeId + "'.");
            }

            if (!node.has("type") || node.get("type").asText("").isBlank()) {
                return WorkflowValidationResult.invalid("Node '" + nodeId + "' has a missing or blank node type.");
            }
            String type = node.get("type").asText();
            if (!registry.isRegisteredType(type)) {
                return WorkflowValidationResult.invalid("Unknown or unregistered node type: '" + type + "' for node '" + nodeId + "'.");
            }

            if (registry.isTriggerType(type)) {
                hasTrigger = true;
            }

            // 9. Basic node configuration validation
            WorkflowValidationResult configCheck = validateNodeConfig(nodeId, type, node.path("data"));
            if (!configCheck.isValid()) {
                return configCheck;
            }
        }

        // 8. Trigger node requirement — DEPLOYMENT ONLY. A manual/execution-mode run of a
        // standalone node (e.g. just a Summarizer with Direct Input Text) is legitimate;
        // WorkflowExecutor falls back to zero-incoming nodes as manual start points in
        // that case. See WorkflowExecutor's trigger-seeding logic.
        if (requireTrigger && !hasTrigger) {
            return WorkflowValidationResult.invalid("Workflow has no trigger node (e.g. Webhook, Cron, GitHub Event, or Email Received).");
        }

        // 5. Every edge has source and target, 6/7. Edge source and target exist, 10. No duplicate edges
        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();

        if (canvasEdges != null && canvasEdges.isArray()) {
            for (JsonNode edge : canvasEdges) {
                if (!edge.has("source") || edge.get("source").asText("").isBlank()) {
                    return WorkflowValidationResult.invalid("Workflow contains an edge with a missing or blank source.");
                }
                if (!edge.has("target") || edge.get("target").asText("").isBlank()) {
                    return WorkflowValidationResult.invalid("Workflow contains an edge with a missing or blank target.");
                }

                String source = edge.get("source").asText();
                String target = edge.get("target").asText();

                if (!nodeIds.contains(source)) {
                    return WorkflowValidationResult.invalid("Edge source node '" + source + "' does not exist in workflow nodes.");
                }
                if (!nodeIds.contains(target)) {
                    return WorkflowValidationResult.invalid("Edge target node '" + target + "' does not exist in workflow nodes.");
                }

                String branch = edge.path("data").path("branch").asText("");
                String edgeKey = source + "->" + target + (branch.isEmpty() ? "" : ":" + branch);

                if (!edgeKeys.add(edgeKey)) {
                    return WorkflowValidationResult.invalid("Duplicate edge detected from '" + source + "' to '" + target + "'.");
                }

                graph.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            }
        }

        // 11. Cycle detection (DAG validation)
        List<String> cyclePath = detectCyclePath(nodeIds, graph);
        if (cyclePath != null) {
            String cycleStr = String.join(" -> ", cyclePath);
            return WorkflowValidationResult.invalid("Workflow contains a cycle: " + cycleStr + ". Cyclic execution is not supported.");
        }

        return WorkflowValidationResult.valid();
    }

    private WorkflowValidationResult validateNodeConfig(String nodeId, String type, JsonNode data) {
        if ("summarizer".equalsIgnoreCase(type)) {
            if (data.has("maxLength")) {
                try {
                    int ml = Integer.parseInt(data.get("maxLength").asText());
                    if (ml <= 0) {
                        return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Summarizer) has invalid maxLength: " + ml);
                    }
                } catch (NumberFormatException e) {
                    return WorkflowValidationResult.invalid("Node '" + nodeId + "' (Summarizer) has non-numeric maxLength.");
                }
            }
        }
        return WorkflowValidationResult.valid();
    }

    private List<String> detectCyclePath(Set<String> nodeIds, Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String nodeId : nodeIds) {
            List<String> path = new ArrayList<>();
            if (dfsCycle(nodeId, graph, visited, recStack, path)) {
                return path;
            }
        }
        return null;
    }

    private boolean dfsCycle(String curr, Map<String, List<String>> graph, Set<String> visited, Set<String> recStack, List<String> path) {
        if (recStack.contains(curr)) {
            path.add(curr);
            return true;
        }
        if (visited.contains(curr)) {
            return false;
        }

        visited.add(curr);
        recStack.add(curr);
        path.add(curr);

        for (String neighbor : graph.getOrDefault(curr, Collections.emptyList())) {
            if (dfsCycle(neighbor, graph, visited, recStack, path)) {
                return true;
            }
        }

        recStack.remove(curr);
        path.remove(path.size() - 1);
        return false;
    }
}
