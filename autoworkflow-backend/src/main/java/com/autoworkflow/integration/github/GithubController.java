package com.autoworkflow.integration.github;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Manual end-to-end tests for the GitHub connection, now backed by the split resource clients. */
@RestController
@RequestMapping("/api/integrations/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubIssueClient githubIssueClient;
    private final GithubRepositoryClient githubRepositoryClient;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GithubIssueResponse> createIssue(@Valid @RequestBody CreateGithubIssueRequest request) {
        String accessToken = integrationService.getDecryptedAccessToken(
                currentUserProvider.getCurrentUserId(), "github");

        GithubIssueResponse issue = githubIssueClient.createIssue(
                accessToken, request.owner(), request.repo(), request.title(), request.body(), request.labels());

        return ApiResponse.success(issue, "Issue #" + issue.number() + " created in " + request.owner() + "/" + request.repo());
    }

    @PostMapping("/repos")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GithubRepositoryResponse> createRepo(@Valid @RequestBody CreateGithubRepoRequest request) {
        String accessToken = integrationService.getDecryptedAccessToken(
                currentUserProvider.getCurrentUserId(), "github");

        GithubRepositoryResponse repo = githubRepositoryClient.createRepository(
                accessToken, request.name(), request.description(), request.isPrivate());

        return ApiResponse.success(repo, "Repository " + repo.fullName() + " created");
    }
}
