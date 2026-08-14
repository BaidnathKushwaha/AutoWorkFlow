package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.execution.strategy.github.GithubActionExecutor;
import com.autoworkflow.execution.strategy.github.GithubActionExecutorRegistry;
import com.autoworkflow.execution.strategy.github.GithubNodeConfig;
import com.autoworkflow.integration.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The "GitHub" node. No switch statement — it looks up the right
 * GithubActionExecutor by the config's `action` field via the registry.
 * Adding a new GitHub action never touches this class again.
 */
@Component
@RequiredArgsConstructor
public class GithubIntegrationStrategy implements NodeStrategy {

    private final GithubActionExecutorRegistry actionExecutorRegistry;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "github"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String accessToken = integrationService.getDecryptedAccessToken(ctx.getUserId(), "github");
        GithubNodeConfig config = GithubNodeConfig.from(ctx.getNodeConfig());

        GithubActionExecutor executor = actionExecutorRegistry.resolve(config.action());
        if (executor == null) {
            return NodeExecutionResult.failed(
                    "Unknown GitHub node action: " + config.action() + ". Available: " + actionExecutorRegistry.all().keySet());
        }

        return executor.execute(config, accessToken);
    }
}
