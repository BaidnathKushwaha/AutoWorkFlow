package com.autoworkflow.execution.strategy.github;

import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.strategy.support.IntegrationNodeOutput;
import com.autoworkflow.integration.github.GithubPullRequestClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MergePullRequestExecutor implements GithubActionExecutor {

    private final GithubPullRequestClient githubPullRequestClient;

    @Override public String getActionKey() { return "merge_pull_request"; }

    @Override
    public NodeExecutionResult execute(GithubNodeConfig config, String accessToken) {
        JsonNode response = githubPullRequestClient.mergePullRequest(
                accessToken, config.owner(), config.repo(), config.pullNumber(), config.body());

        boolean merged = response.path("merged").asBoolean(false);
        if (!merged) {
            return NodeExecutionResult.failed("GitHub did not merge the pull request: " + response.path("message").asText("unknown reason"));
        }

        return NodeExecutionResult.ok(IntegrationNodeOutput.success("github", "merge_pull_request", config.pullNumber(), null));
    }
}
