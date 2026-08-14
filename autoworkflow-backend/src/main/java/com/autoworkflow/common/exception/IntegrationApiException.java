package com.autoworkflow.common.exception;

import lombok.Getter;

/**
 * Standardized failure shape for every outbound integration API call,
 * regardless of provider. Carries enough structure (provider, operation,
 * httpStatus, retryable) for GlobalExceptionHandler / logging / the frontend
 * to react intelligently, while the `message` is always a clean, human
 * sentence — never a raw provider error blob.
 *
 * Lives alongside its parent IntegrationException here in common.exception
 * (not in the integration.http package) so GlobalExceptionHandler doesn't
 * need a dependency pointing back into the integration module.
 */
@Getter
public class IntegrationApiException extends IntegrationException {

    private final String provider;
    private final String operation;
    private final Integer httpStatus; // null for transport-level failures (timeout, connection reset, ...)
    private final boolean retryable;

    private IntegrationApiException(String provider, String operation, Integer httpStatus, boolean retryable,
                                     String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.operation = operation;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public static IntegrationApiException http(String provider, String operation, int httpStatus,
                                                String friendlyMessage, boolean retryable, Throwable cause) {
        return new IntegrationApiException(provider, operation, httpStatus, retryable, friendlyMessage, cause);
    }

    public static IntegrationApiException transport(String provider, String operation, Throwable cause) {
        return new IntegrationApiException(provider, operation, null, true,
                provider + " didn't respond in time. Try again in a moment.", cause);
    }

    /** For body-level app errors (e.g. Slack's `ok:false`) that aren't worth retrying. */
    public static IntegrationApiException nonRetryable(String provider, String operation, String friendlyMessage) {
        return new IntegrationApiException(provider, operation, null, false, friendlyMessage, null);
    }
}
