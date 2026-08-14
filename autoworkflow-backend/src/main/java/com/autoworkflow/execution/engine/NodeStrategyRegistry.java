package com.autoworkflow.execution.engine;

import com.autoworkflow.common.exception.NodeExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Wires every NodeStrategy bean (one per node type in the marketplace) into a lookup map by type key. */
@Component
@RequiredArgsConstructor
public class NodeStrategyRegistry {

    /**
     * Legacy/alias type keys that should resolve to a canonical strategy's real key.
     * The frontend palette historically used different names than the backend
     * strategy keys (see Phase 1 of the canonical node-type contract fix); this
     * map keeps already-saved workflows using the old names working even after
     * the palette itself is updated to emit canonical names for new nodes.
     *
     *   cron           -> cron_trigger
     *   email_trigger  -> email_received
     *   http           -> http_request
     *   github_action  -> github          (GithubIntegrationStrategy)
     *   sheets         -> google_sheets
     *   email_send     -> send_email
     *   if             -> if_condition
     *   openai         -> ai              (AiNodeStrategy, generic AI completion node)
     */
    private static final Map<String, String> LEGACY_TYPE_ALIASES = Map.ofEntries(
            Map.entry("openai", "ai"),
            Map.entry("cron", "cron_trigger"),
            Map.entry("email_trigger", "email_received"),
            Map.entry("http", "http_request"),
            Map.entry("github_action", "github"),
            Map.entry("sheets", "google_sheets"),
            Map.entry("email_send", "send_email"),
            Map.entry("if", "if_condition")
    );

    private final List<NodeStrategy> strategies;
    private Map<String, NodeStrategy> byTypeKey;

    private Map<String, NodeStrategy> map() {
        if (byTypeKey == null) {
            byTypeKey = strategies.stream().collect(Collectors.toMap(NodeStrategy::getTypeKey, s -> s));
        }
        return byTypeKey;
    }

    public NodeStrategy resolve(String nodeType) {
        String canonicalType = LEGACY_TYPE_ALIASES.getOrDefault(nodeType, nodeType);
        NodeStrategy strategy = map().get(canonicalType);
        if (strategy == null) {
            throw new NodeExecutionException(nodeType, nodeType, "No execution strategy registered for node type: " + nodeType, null);
        }
        return strategy;
    }

    /**
     * Non-throwing check used by WorkflowExecutor to decide which zero-incoming-edge
     * nodes are legitimate execution start points (webhook, cron_trigger, github_event,
     * email_received) versus a disconnected/orphaned action node that just happens to
     * have no incoming edges (e.g. a Slack node the user forgot to wire up) — those must
     * NOT be seeded and executed. Returns false for unknown node types.
     */
    public boolean isTriggerType(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) return false;
        String canonicalType = LEGACY_TYPE_ALIASES.getOrDefault(nodeType, nodeType);
        NodeStrategy strategy = map().get(canonicalType);
        return strategy != null && strategy.isTrigger();
    }

    public boolean isRegisteredType(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) return false;
        String canonicalType = LEGACY_TYPE_ALIASES.getOrDefault(nodeType, nodeType);
        return map().containsKey(canonicalType);
    }
}
