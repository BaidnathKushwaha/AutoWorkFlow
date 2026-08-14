package com.autoworkflow.execution.strategy.github;

import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.strategy.support.IntegrationNodeOutput;
import com.autoworkflow.integration.github.GithubIssueClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentExecutor implements GithubActionExecutor {

    private final GithubIssueClient githubIssueClient;

    @Override public String getActionKey() { return "comment_on_pr"; }

    @Override
    public NodeExecutionResult execute(GithubNodeConfig config, String accessToken) {
        JsonNode response = githubIssueClient.commentOnIssue(
                accessToken, config.owner(), config.repo(), config.issueNumber(), config.body());

        String commentUrl = response.path("html_url").asText(null);
        String commentId = response.path("id").asText(null);

        return NodeExecutionResult.ok(IntegrationNodeOutput.success("github", "comment_on_pr", commentId, commentUrl));
    }
}
