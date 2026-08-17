package com.autoworkflow.common.llm;

/**
 * Structured provider error. Execution strategies and the WorkflowExecutor read
 * {@link #getMessage()} for the string that ends up in LogStep.error / the
 * execution UI, so it must always be a short, user-facing sentence — never a
 * raw HTTP body or stack trace (those may contain request internals and
 * should only go to server logs, via {@link #getTechnicalDetail()}).
 */
public class AiProviderException extends AiException {

    private final String provider;
    private final Integer httpStatus;
    private final String code;
    private final String technicalDetail;

    public AiProviderException(String provider, Integer httpStatus, String code, String cleanMessage, String technicalDetail) {
        super(cleanMessage);
        this.provider = provider;
        this.httpStatus = httpStatus;
        this.code = code;
        this.technicalDetail = technicalDetail;
    }

    public String getProvider() { return provider; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }

    /** Full raw provider response — safe for server-side logs/diagnostics, never for LogStep.error or the UI. */
    public String getTechnicalDetail() { return technicalDetail; }

    /**
     * Classifies a raw HTTP status + provider error body into a short, actionable
     * message. Shared by GeminiClient and OpenAiClient so both providers report
     * failures the same way instead of leaking raw JSON to the execution UI.
     */
    public static AiProviderException from(String provider, int httpStatus, String rawBody) {
        String lower = rawBody == null ? "" : rawBody.toLowerCase();
        String code;
        String message;

        if (
                httpStatus == 429
                        || (
                        "openrouter".equalsIgnoreCase(provider)
                                && httpStatus == 402
                )
                        || lower.contains("resource_exhausted")
                        || lower.contains("insufficient_quota")
                        || lower.contains("quota")
        ) {
            code = "QUOTA_EXCEEDED";
            message =
                    capitalize(provider)
                            + " quota exceeded. Check your "
                            + capitalize(provider)
                            + " plan/billing, or switch this node to a different connected provider.";
        } else if (httpStatus == 401 || httpStatus == 403 || lower.contains("invalid_api_key") || lower.contains("permission_denied") || lower.contains("api_key_invalid")) {
            code = "AUTH_FAILED";
            message = capitalize(provider) + " rejected the API key. Reconnect " + capitalize(provider) + " in Integrations.";
        } else if (httpStatus == 400 && (lower.contains("model") || lower.contains("not_found"))) {
            code = "INVALID_MODEL";
            message = capitalize(provider) + " rejected the request — the selected model may not exist or isn't available for this key.";
        } else if (httpStatus >= 500) {
            code = "PROVIDER_UNAVAILABLE";
            message = capitalize(provider) + " is temporarily unavailable (HTTP " + httpStatus + "). Try again shortly.";
        } else {
            code = "REQUEST_FAILED";
            message = capitalize(provider) + " request failed (HTTP " + httpStatus + ").";
        }

        return new AiProviderException(provider, httpStatus, code, message, rawBody);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "The AI provider";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
