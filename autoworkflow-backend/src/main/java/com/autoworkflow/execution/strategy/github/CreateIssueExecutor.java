package com.autoworkflow.execution.strategy.github;

import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.strategy.support.IntegrationNodeOutput;
import com.autoworkflow.integration.github.GithubIssueClient;
import com.autoworkflow.integration.github.GithubIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateIssueExecutor implements GithubActionExecutor {

    private final GithubIssueClient githubIssueClient;

    @Override public String getActionKey() { return "create_issue"; }

    @Override
    public NodeExecutionResult execute(GithubNodeConfig config, String accessToken) {
        GithubIssueResponse issue = githubIssueClient.createIssue(
                accessToken, config.owner(), config.repo(), config.title(), config.body(), config.labels());

        return NodeExecutionResult.ok(IntegrationNodeOutput.success(
                "github", "create_issue", String.valueOf(issue.number()), issue.htmlUrl()));
    }
}
