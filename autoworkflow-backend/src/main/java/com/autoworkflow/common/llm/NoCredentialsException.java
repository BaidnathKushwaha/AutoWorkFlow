package com.autoworkflow.common.llm;

/**
 * Thrown by provider clients specifically when NO credentials are configured at all
 * (no user-connected key AND no platform key) — as opposed to credentials existing
 * but being rejected (see AiProviderException's AUTH_FAILED code).
 *
 * This distinction matters for AUTO provider mode: "this provider was never set up"
 * is a reasonable, silent skip-to-next-provider case, while "this provider's
 * credentials were rejected" is a real, actionable problem that AUTO should surface
 * immediately rather than paper over with a fallback attempt.
 */
public class NoCredentialsException extends AiException {
    public NoCredentialsException(String message) {
        super(message);
    }
}
