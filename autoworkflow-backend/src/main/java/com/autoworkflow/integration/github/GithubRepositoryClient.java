package com.autoworkflow.integration.github;

import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** GitHub Repositories API — currently just repo creation; grows independently of issues/PRs. */
@Component
@RequiredArgsConstructor
public class GithubRepositoryClient {

    private static final String API_BASE = "https://api.github.com";

    private final WebClient.Builder webClientBuilder;
    private final IntegrationApiExecutor apiExecutor;

    public GithubRepositoryResponse createRepository(String accessToken, String name, String description, boolean isPrivate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        if (description != null) payload.put("description", description);
        payload.put("private", isPrivate);

        JsonNode response = apiExecutor.execute("github", "create_repo", () ->
                webClientBuilder.build().post()
                        .uri(API_BASE + "/user/repos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30))
                        .block());

        return GithubRepositoryResponse.fromApiResponse(response);
    }
}
