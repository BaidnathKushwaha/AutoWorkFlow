package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Webhook trigger node — passes the raw payload through, but also enriches it
 * with a flat set of well-known fields so downstream AI/Summarizer nodes can
 * always find the text they need without knowing the source schema.
 *
 * GitHub Push payload → enriched with:
 *   text    : full human-readable summary of the push event
 *   message : first commit message
 *   branch  : branch name (ref stripped)
 *   repo    : repository full name
 *   pusher  : pusher login / name
 *
 * Generic payload → kept as-is; a `text` field is added if not already present
 * (stringified JSON) so summariser always has something to work with.
 */
@Component
public class WebhookTriggerStrategy implements NodeStrategy {

    @Override
    public String getTypeKey() { return "webhook"; }

    @Override
    public boolean isTrigger() { return true; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode raw = ctx.getInputPayload();

        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return NodeExecutionResult.ok(JsonUtils.mapper().createObjectNode());
        }

        // If payload is already a plain string / scalar, just wrap it
        if (!raw.isObject() && !raw.isArray()) {
            ObjectNode out = JsonUtils.mapper().createObjectNode();
            out.put("text", raw.asText());
            return NodeExecutionResult.ok(out);
        }

        ObjectNode out = JsonUtils.mapper().createObjectNode();

        // ---- GitHub push event detection ----
        boolean isGitHubPush = raw.has("commits") && raw.get("commits").isArray()
                && raw.get("commits").size() > 0;

        if (isGitHubPush) {
            JsonNode firstCommit = raw.get("commits").get(0);
            String commitMsg     = firstCommit.path("message").asText("");
            String authorName    = firstCommit.path("author").path("name").asText(
                                   firstCommit.path("author").path("username").asText("unknown"));
            String branch        = stripRefsHeads(raw.path("ref").asText("main"));
            String repoName      = raw.path("repository").path("full_name")
                                      .asText(raw.path("repository").path("name").asText("unknown-repo"));
            String pusher        = raw.path("pusher").path("name")
                                      .asText(raw.path("sender").path("login").asText(authorName));
            int    totalCommits  = raw.get("commits").size();

            // Build a rich human-readable text for the AI summariser
            StringBuilder sb = new StringBuilder();
            sb.append("GitHub Push Event\n");
            sb.append("Repository: ").append(repoName).append("\n");
            sb.append("Branch: ").append(branch).append("\n");
            sb.append("Pusher: ").append(pusher).append("\n");
            sb.append("Total commits: ").append(totalCommits).append("\n\n");
            sb.append("Commits:\n");
            raw.get("commits").forEach(c -> {
                String sha = c.path("id").asText(c.path("sha").asText("")).substring(0, Math.min(7,
                        c.path("id").asText(c.path("sha").asText("0000000")).length()));
                sb.append("  [").append(sha).append("] ")
                  .append(c.path("message").asText("(no message)")).append("\n");
            });

            out.put("text",    sb.toString().trim());
            out.put("message", commitMsg);
            out.put("branch",  branch);
            out.put("repo",    repoName);
            out.put("pusher",  pusher);
            out.put("commitCount", totalCommits);
            // Preserve the raw payload under a sub-key for advanced nodes
            out.set("raw", raw);

        } else {
            // ---- Generic webhook payload ----
            // Copy all existing fields
            raw.fields().forEachRemaining(e -> out.set(e.getKey(), e.getValue()));

            // Always ensure a `text` field exists for AI/summariser nodes
            if (!out.has("text") || out.path("text").asText("").isBlank()) {
                // Try common message-like fields
                String text = firstNonBlank(
                    raw.path("message").asText(""),
                    raw.path("body").asText(""),
                    raw.path("content").asText(""),
                    raw.path("description").asText(""),
                    raw.path("summary").asText("")
                );
                out.put("text", text.isBlank() ? raw.toString() : text);
            }
        }

        return NodeExecutionResult.ok(out);
    }

    private String stripRefsHeads(String ref) {
        if (ref.startsWith("refs/heads/")) return ref.substring("refs/heads/".length());
        if (ref.startsWith("refs/tags/"))  return ref.substring("refs/tags/".length());
        return ref;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
