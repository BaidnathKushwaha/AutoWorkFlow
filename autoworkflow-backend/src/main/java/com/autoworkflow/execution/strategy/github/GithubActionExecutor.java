package com.autoworkflow.execution.strategy.github;

import com.autoworkflow.execution.engine.NodeExecutionResult;

/**
 * One implementation per GitHub node action. Adding a new action (e.g.
 * "close_issue") means writing one new class here and nothing else —
 * GithubIntegrationStrategy just looks it up by key, same pattern as
 * OAuthTokenExchangeClient / IntegrationErrorMapper elsewhere in this module.
 */
public interface GithubActionExecutor {

    /** Must match the `action` value in the node's config, e.g. "create_issue". */
    String getActionKey();

    NodeExecutionResult execute(GithubNodeConfig config, String accessToken);
}
