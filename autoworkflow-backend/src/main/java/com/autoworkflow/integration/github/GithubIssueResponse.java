package com.autoworkflow.integration.github;

import com.fasterxml.jackson.databind.JsonNode;

/** Clean, stable shape for a created GitHub issue — callers don't need to know GitHub's raw JSON. */
public record GithubIssueResponse(
        long number,
        String title,
        String htmlUrl,
        String state
) {
    public static GithubIssueResponse fromApiResponse(JsonNode raw) {
        return new GithubIssueResponse(
                raw.path("number").asLong(),
                raw.path("title").asText(),
                raw.path("html_url").asText(),
                raw.path("state").asText()
        );
    }
}
