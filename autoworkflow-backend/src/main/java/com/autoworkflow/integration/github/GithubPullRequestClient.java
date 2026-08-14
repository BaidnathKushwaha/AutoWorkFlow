package com.autoworkflow.integration.github;

import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/** GitHub Pull Requests API — split out so PR-specific actions (merge, review, etc.) live on their own client. */
@Component
@RequiredArgsConstructor
public class GithubPullRequestClient {

    private static final String API_BASE = "https://api.github.com";

    private final WebClient.Builder webClientBuilder;
    private final IntegrationApiExecutor apiExecutor;

    public JsonNode mergePullRequest(String accessToken, String owner, String repo, String pullNumber, String commitMessage) {
        String url = "%s/repos/%s/%s/pulls/%s/merge".formatted(API_BASE, owner, repo, pullNumber);

        return apiExecutor.execute("github", "merge_pull_request", () ->
                webClientBuilder.build().put()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .bodyValue(Map.of("commit_message", commitMessage == null ? "" : commitMessage))
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30))
                        .block());
    }
}
