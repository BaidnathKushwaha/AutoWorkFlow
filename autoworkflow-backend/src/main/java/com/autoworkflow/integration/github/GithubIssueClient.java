package com.autoworkflow.integration.github;

import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GitHub Issues API — split out of the old monolithic GithubApiClient so it only knows about issues. */
@Component
@RequiredArgsConstructor
public class GithubIssueClient {

    private static final String API_BASE = "https://api.github.com";

    private final WebClient.Builder webClientBuilder;
    private final IntegrationApiExecutor apiExecutor;

    public GithubIssueResponse createIssue(String accessToken, String owner, String repo,
                                            String title, String body, List<String> labels) {
        String url = "%s/repos/%s/%s/issues".formatted(API_BASE, owner, repo);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        if (body != null) payload.put("body", body);
        if (labels != null && !labels.isEmpty()) payload.put("labels", labels);

        JsonNode response = apiExecutor.execute("github", "create_issue", () -> post(url, accessToken, payload));
        return GithubIssueResponse.fromApiResponse(response);
    }

    public JsonNode commentOnIssue(String accessToken, String owner, String repo, String issueNumber, String body) {
        String url = "%s/repos/%s/%s/issues/%s/comments".formatted(API_BASE, owner, repo, issueNumber);
        return apiExecutor.execute("github", "comment_on_pr", () -> post(url, accessToken, Map.of("body", body)));
    }

    private JsonNode post(String url, String accessToken, Object body) {
        return webClientBuilder.build().post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}
