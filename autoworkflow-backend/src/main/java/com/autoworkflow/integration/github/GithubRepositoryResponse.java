package com.autoworkflow.integration.github;

import com.fasterxml.jackson.databind.JsonNode;

public record GithubRepositoryResponse(String fullName, String htmlUrl, boolean isPrivate) {
    public static GithubRepositoryResponse fromApiResponse(JsonNode raw) {
        return new GithubRepositoryResponse(
                raw.path("full_name").asText(),
                raw.path("html_url").asText(),
                raw.path("private").asBoolean(false)
        );
    }
}
