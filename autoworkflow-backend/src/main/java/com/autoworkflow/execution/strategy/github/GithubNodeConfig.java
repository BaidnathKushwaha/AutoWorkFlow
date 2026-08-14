package com.autoworkflow.execution.strategy.github;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed view over a GitHub node's raw JSON config, replacing scattered
 * `config.path("owner").asText()` calls with compile-time-safe field access.
 * Covers the union of fields each GithubActionExecutor needs; each executor
 * only reads the fields relevant to its own action.
 */
public record GithubNodeConfig(
        String action,
        String owner,
        String repo,
        String title,
        String body,
        String issueNumber,
        List<String> labels,
        String repoName,
        String repoDescription,
        boolean repoPrivate,
        String pullNumber
) {
    public static GithubNodeConfig from(JsonNode node) {
        List<String> labels = new ArrayList<>();
        node.path("labels").forEach(n -> labels.add(n.asText()));

        return new GithubNodeConfig(
                node.path("action").asText("comment_on_pr"),
                node.path("owner").asText(),
                node.path("repo").asText(),
                node.path("title").asText(null),
                node.path("body").asText(null),
                node.path("issueNumber").asText(null),
                labels,
                node.path("repoName").asText(null),
                node.path("repoDescription").asText(null),
                node.path("repoPrivate").asBoolean(true),
                node.path("pullNumber").asText(null)
        );
    }
}
