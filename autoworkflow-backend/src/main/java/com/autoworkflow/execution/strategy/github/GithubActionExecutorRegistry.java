package com.autoworkflow.execution.strategy.github;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replaces the old `Map<String, GithubActionExecutor>` @Bean (GithubActionExecutorConfig)
 * — same bug as OAuthTokenExchangeRegistry fixes: that bean was never actually
 * reachable, because Spring always resolves a `Map<String, T>` injection
 * point as "every T bean keyed by *bean name*", not by looking for an
 * explicit Map<String,T> bean. Since CreateIssueExecutor, CommentExecutor,
 * etc. are plain `@Component` (no custom name), the real keys were
 * "createIssueExecutor", "commentExecutor", ... — never "create_issue",
 * "comment_on_pr", etc. Every GitHub node action was silently unresolvable.
 */
@Component
public class GithubActionExecutorRegistry {

    private final Map<String, GithubActionExecutor> executorsByAction;

    public GithubActionExecutorRegistry(List<GithubActionExecutor> executors) {
        this.executorsByAction = executors.stream()
                .collect(Collectors.toMap(GithubActionExecutor::getActionKey, Function.identity()));
    }

    public GithubActionExecutor resolve(String action) {
        return executorsByAction.get(action);
    }

    public Map<String, GithubActionExecutor> all() {
        return executorsByAction;
    }
}
