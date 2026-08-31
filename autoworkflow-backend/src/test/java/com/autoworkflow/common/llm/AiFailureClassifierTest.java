package com.autoworkflow.common.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiFailureClassifierTest {

    @Test
    void noCredentialsException_isRetryable() {
        assertThat(AiFailureClassifier.isRetryable(new NoCredentialsException("no key"))).isTrue();
    }

    @Test
    void quotaExceeded429_isRetryable() {
        assertThat(AiFailureClassifier.isRetryable(
                AiProviderException.from("openrouter", 429, "{}")
        )).isTrue();
    }

    @Test
    void openRouter402_isRetryable() {
        AiProviderException exception =
                AiProviderException.from("openrouter", 402, "{\"error\":\"payment required\"}");

        assertThat(exception.getCode()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(AiFailureClassifier.isRetryable(exception)).isTrue();
    }

    @Test
    void providerUnavailable5xx_isRetryable() {
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("gemini", 503, "{}"))).isTrue();
    }

    @Test
    void allProviderUnavailable5xxStatuses_areRetryable() {
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 500, "{}"))).isTrue();
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 502, "{}"))).isTrue();
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 503, "{}"))).isTrue();
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 504, "{}"))).isTrue();
    }

    @Test
    void authFailed_isNotRetryable() {
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 401, "{}"))).isFalse();
        assertThat(AiFailureClassifier.isRetryable(AiProviderException.from("openai", 403, "{}"))).isFalse();
    }

    @Test
    void invalidModel_isNotRetryable() {
        AiProviderException exception =
                AiProviderException.from("openrouter", 400, "{\"error\":\"model not_found\"}");

        assertThat(exception.getCode()).isEqualTo("INVALID_MODEL");
        assertThat(AiFailureClassifier.isRetryable(exception)).isFalse();
    }

    @Test
    void genericRequestFailed4xx_isNotRetryable() {
        AiProviderException exception =
                AiProviderException.from("openai", 422, "{}");

        assertThat(exception.getCode()).isEqualTo("REQUEST_FAILED");
        assertThat(AiFailureClassifier.isRetryable(exception)).isFalse();
    }

    @Test
    void genericAiException_networkOrTimeoutStyle_isRetryable() {
        assertThat(AiFailureClassifier.isRetryable(
                new AiException("OpenAI request failed: connection reset")
        )).isTrue();
    }

    @Test
    void unexpectedExceptionType_failsClosed_notRetryable() {
        assertThat(AiFailureClassifier.isRetryable(new IllegalStateException("unrelated"))).isFalse();
        assertThat(AiFailureClassifier.isRetryable(new RuntimeException("unrelated"))).isFalse();
        assertThat(AiFailureClassifier.isRetryable(new NullPointerException())).isFalse();
    }

    @Test
    void safeSummary_neverIncludesRawExceptionMessage() {
        AiProviderException withSensitiveBody = AiProviderException.from(
                "openrouter",
                429,
                "{\"authorization\":\"Bearer sk-super-secret-value\"}"
        );

        String summary = AiFailureClassifier.safeSummary(withSensitiveBody);

        assertThat(summary).doesNotContain("sk-super-secret-value");
        assertThat(summary).doesNotContain("Bearer");
        assertThat(summary).isEqualTo("rate limited / quota exceeded");
    }

    @Test
    void safeSummary_coversEveryClassifiedCode() {
        assertThat(AiFailureClassifier.safeSummary(new NoCredentialsException("x")))
                .isEqualTo("no credentials configured");
        assertThat(AiFailureClassifier.safeSummary(AiProviderException.from("x", 429, "{}")))
                .contains("rate limited");
        assertThat(AiFailureClassifier.safeSummary(AiProviderException.from("x", 401, "{}")))
                .contains("authentication");
        assertThat(AiFailureClassifier.safeSummary(AiProviderException.from("x", 400, "{\"error\":\"model not_found\"}")))
                .contains("model");
        assertThat(AiFailureClassifier.safeSummary(AiProviderException.from("x", 503, "{}")))
                .contains("unavailable");
        assertThat(AiFailureClassifier.safeSummary(new AiException("network blip")))
                .contains("timeout or connection");
    }
}
