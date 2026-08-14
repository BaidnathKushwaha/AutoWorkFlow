package com.autoworkflow.integration.http;

/**
 * One implementation per provider, translating that provider's specific
 * error vocabulary into a clean, actionable message. New provider = new
 * mapper bean; IntegrationApiExecutor never needs a new `if` branch.
 */
public interface IntegrationErrorMapper {

    String getProviderKey();

    /** Maps an HTTP-level failure (4xx/5xx). Return null to fall back to a generic message. */
    String mapHttpError(int status, String rawProviderMessage);

    /** Maps a body-level app error code (e.g. Slack's `error` field) when the provider returns HTTP 200 on failure. */
    default String mapAppError(String errorCode, String rawProviderMessage) {
        return null;
    }
}
