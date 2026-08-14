package com.autoworkflow.integration.github;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateGithubIssueRequest(
        @NotBlank(message = "Repository owner is required") String owner,
        @NotBlank(message = "Repository name is required") String repo,
        @NotBlank(message = "Issue title is required") String title,
        String body,
        List<String> labels
) {}
