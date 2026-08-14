package com.autoworkflow.integration.github;

import jakarta.validation.constraints.NotBlank;

public record CreateGithubRepoRequest(
        @NotBlank(message = "Repository name is required") String name,
        String description,
        boolean isPrivate
) {}
