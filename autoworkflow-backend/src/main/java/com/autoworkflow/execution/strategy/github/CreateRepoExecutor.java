package com.autoworkflow.execution.strategy.github;

import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.strategy.support.IntegrationNodeOutput;
import com.autoworkflow.integration.github.GithubRepositoryClient;
import com.autoworkflow.integration.github.GithubRepositoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** New capability, added as one class with zero changes to GithubIntegrationStrategy — proves the OCP claim. */
@Component
@RequiredArgsConstructor
public class CreateRepoExecutor implements GithubActionExecutor {

    private final GithubRepositoryClient githubRepositoryClient;

    @Override public String getActionKey() { return "create_repo"; }

    @Override
    public NodeExecutionResult execute(GithubNodeConfig config, String accessToken) {
        GithubRepositoryResponse repo = githubRepositoryClient.createRepository(
                accessToken, config.repoName(), config.repoDescription(), config.repoPrivate());

        return NodeExecutionResult.ok(IntegrationNodeOutput.success("github", "create_repo", repo.fullName(), repo.htmlUrl()));
    }
}
