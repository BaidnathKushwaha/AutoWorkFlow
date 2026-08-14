package com.autoworkflow.integration.slack;

import com.autoworkflow.integration.http.IntegrationErrorMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Slack mostly reports failure via a body-level `error` code on an HTTP 200
 * response, not an HTTP status — so mapAppError does most of the work here.
 */
@Component
public class SlackErrorMapper implements IntegrationErrorMapper {

    private static final Map<String, String> APP_ERROR_MESSAGES = Map.of(
            "channel_not_found", "Slack channel not found. Check the channel name or ID.",
            "not_in_channel", "The AutoWorkflow bot isn't a member of that Slack channel yet — invite it first.",
            "invalid_auth", "Reconnect Slack — your connection has expired or been revoked.",
            "account_inactive", "Reconnect Slack — your connection has expired or been revoked.",
            "token_revoked", "Reconnect Slack — your connection has been revoked.",
            "missing_scope", "Slack denied this action — the connected app is missing a required permission scope.",
            "rate_limited", "Slack rate limit hit. This will retry automatically."
    );

    @Override public String getProviderKey() { return "slack"; }

    @Override
    public String mapHttpError(int status, String rawProviderMessage) {
        return switch (status) {
            case 401, 403 -> "Reconnect Slack — your connection has expired or been revoked.";
            case 429 -> "Slack rate limit hit. This will retry automatically.";
            default -> null;
        };
    }

    @Override
    public String mapAppError(String errorCode, String rawProviderMessage) {
        return APP_ERROR_MESSAGES.get(errorCode);
    }
}
