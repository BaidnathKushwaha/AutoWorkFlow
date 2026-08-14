package com.autoworkflow.integration.http;

import com.autoworkflow.common.exception.IntegrationApiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Priority 2 (#5 exception mapping) + Priority 3 (#7 retry) in one place.
 *
 * Every API client (GithubIssueClient, GithubRepositoryClient, SlackMessageClient, ...)
 * routes its calls through here instead of handling retry/errors itself.
 * A new provider's client reuses this unchanged — only its
 * IntegrationErrorMapper bean needs to exist for friendly messages.
 */
@Component
@Slf4j
public class IntegrationApiExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofMillis(300);
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);

    private final Map<String, IntegrationErrorMapper> errorMappers;

    public IntegrationApiExecutor(List<IntegrationErrorMapper> mappers) {
        this.errorMappers = mappers.stream()
                .collect(Collectors.toMap(IntegrationErrorMapper::getProviderKey, Function.identity()));
    }

    /**
     * Runs `call`, retrying transient failures (timeouts, 429/5xx) with
     * exponential backoff, and converting any final failure into a clean
     * IntegrationApiException via that provider's error mapper.
     */
    public <T> T execute(String provider, String operation, Supplier<T> call) {
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                return call.get();
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = RETRYABLE_STATUSES.contains(status);
                String rawMessage = extractRawMessage(e);
                String friendly = mapHttpError(provider, status, rawMessage);

                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw IntegrationApiException.http(provider, operation, status, friendly, retryable, e);
                }
                log.warn("[{}] {} failed (attempt {}/{}, HTTP {}), retrying: {}", provider, operation, attempt, MAX_ATTEMPTS, status, rawMessage);
                backoff(attempt);
            } catch (IntegrationApiException e) {
                // Already classified (e.g. Slack's ok:false app error) — respect its retryable flag as-is.
                if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("[{}] {} failed (attempt {}/{}): {}", provider, operation, attempt, MAX_ATTEMPTS, e.getMessage());
                backoff(attempt);
            } catch (Exception e) {
                // Timeouts, connection resets, DNS failures, etc.
                if (attempt >= MAX_ATTEMPTS) {
                    throw IntegrationApiException.transport(provider, operation, e);
                }
                log.warn("[{}] {} transport failure (attempt {}/{}): {}", provider, operation, attempt, MAX_ATTEMPTS, e.getMessage());
                backoff(attempt);
            }
        }
    }

    /** For clients that need to raise a body-level app error (e.g. Slack's `error` field) as a mapped, non-retryable failure. */
    public IntegrationApiException appError(String provider, String operation, String errorCode, String rawMessage) {
        IntegrationErrorMapper mapper = errorMappers.get(provider);
        String friendly = mapper != null ? mapper.mapAppError(errorCode, rawMessage) : null;
        return IntegrationApiException.nonRetryable(provider, operation, friendly != null ? friendly : (provider + " rejected the request: " + rawMessage));
    }

    private String mapHttpError(String provider, int status, String rawMessage) {
        IntegrationErrorMapper mapper = errorMappers.get(provider);
        String friendly = mapper != null ? mapper.mapHttpError(status, rawMessage) : null;
        return friendly != null ? friendly : (provider + " request failed (HTTP " + status + ").");
    }

    private String extractRawMessage(WebClientResponseException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            if (body != null) {
                if (body.has("message")) return body.get("message").asText();
                if (body.has("error")) return body.get("error").asText();
            }
        } catch (Exception ignored) {
            // fall through to raw message below
        }
        return e.getMessage();
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF.toMillis() * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
