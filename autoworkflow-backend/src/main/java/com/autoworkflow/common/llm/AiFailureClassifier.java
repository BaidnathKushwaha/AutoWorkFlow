package com.autoworkflow.common.llm;

import java.util.Set;

/**
 * The single place that decides whether AiProviderRouter should try the next
 * configured provider or stop and surface an error immediately. Kept as one small,
 * well-tested classifier rather than scattering retry-vs-stop judgment calls across
 * AiProviderRouter's control flow.
 *
 * RETRYABLE ("provider unavailable right now" — try the next one):
 *   - NoCredentialsException: no credentials configured at all for this provider.
 *   - AiProviderException with code QUOTA_EXCEEDED or PROVIDER_UNAVAILABLE (429, 5xx).
 *   - Any other AiException that is NOT an AiProviderException and NOT a
 *     NoCredentialsException — this is exactly what OpenAiClient/GeminiClient/
 *     OpenRouterClient's `catch (Exception e) { throw new AiException(...) }`
 *     fallback produces for network-level failures (timeout, connection refused,
 *     DNS failure) where no HTTP response was ever received to classify by status.
 *
 * NOT RETRYABLE ("this request/config is actually wrong" — stop, don't paper over it):
 *   - AiProviderException with code AUTH_FAILED (credentials exist but were
 *     rejected — different from "no credentials exist", which IS retryable) or
 *     INVALID_MODEL or REQUEST_FAILED (a generic 4xx that isn't quota/auth/model).
 *   - Anything else unexpected — deliberately fail closed rather than blindly
 *     retrying an exception type nobody has reasoned about.
 */
public final class AiFailureClassifier {

    private static final Set<String> RETRYABLE_CODES = Set.of("QUOTA_EXCEEDED", "PROVIDER_UNAVAILABLE");
    private static final Set<String> PERMANENT_CODES = Set.of("AUTH_FAILED", "INVALID_MODEL", "REQUEST_FAILED");

    private AiFailureClassifier() {}

    public static boolean isRetryable(Throwable e) {
        if (e instanceof NoCredentialsException) {
            return true;
        }
        if (e instanceof AiProviderException ape) {
            if (ape.getCode() != null && RETRYABLE_CODES.contains(ape.getCode())) return true;
            if (ape.getCode() != null && PERMANENT_CODES.contains(ape.getCode())) return false;
            // Unclassified code but a 5xx status still means "server-side, transient".
            return ape.getHttpStatus() != null && ape.getHttpStatus() >= 500;
        }
        if (e instanceof AiException) {
            // Generic AiException, not one of the two structured subtypes above: the
            // clients' catch-all for genuine communication failures (timeout, connection
            // refused, etc.) — see class javadoc. Treat as transient/retryable.
            return true;
        }
        // Unknown exception type nobody has reasoned about -> fail closed, do not retry.
        return false;
    }

    /**
     * A short, fixed, credential-free summary for error metadata/logs — built from a
     * closed set of canned strings keyed by `code`, never from the raw exception
     * message, so there is no path for request/response internals to leak through here.
     */
    public static String safeSummary(Throwable e) {
        if (e instanceof NoCredentialsException) {
            return "no credentials configured";
        }
        if (e instanceof AiProviderException ape && ape.getCode() != null) {
            return switch (ape.getCode()) {
                case "QUOTA_EXCEEDED" -> "rate limited / quota exceeded";
                case "AUTH_FAILED" -> "authentication rejected";
                case "INVALID_MODEL" -> "invalid model";
                case "PROVIDER_UNAVAILABLE" -> "temporarily unavailable";
                default -> "request failed";
            };
        }
        if (e instanceof AiException) {
            return "unavailable (timeout or connection failure)";
        }
        return "unavailable";
    }
}
