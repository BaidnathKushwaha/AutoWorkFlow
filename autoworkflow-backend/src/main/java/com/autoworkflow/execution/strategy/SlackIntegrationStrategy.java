package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.execution.strategy.slack.SlackNodeConfig;
import com.autoworkflow.execution.strategy.support.IntegrationNodeOutput;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.slack.SlackMessageClient;
import com.autoworkflow.integration.slack.SlackMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackIntegrationStrategy implements NodeStrategy {

    private final SlackMessageClient slackMessageClient;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "slack"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String accessToken = integrationService.getDecryptedAccessToken(ctx.getUserId(), "slack");
        SlackNodeConfig config = SlackNodeConfig.from(ctx.getNodeConfig(), ctx.getInputPayload().toString());

        SlackMessageResponse message = slackMessageClient.postMessage(accessToken, config.channel(), config.message());

        return NodeExecutionResult.ok(IntegrationNodeOutput.success("slack", "post_message", message.ts(), null));
    }
}
