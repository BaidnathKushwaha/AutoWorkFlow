package com.autoworkflow.common.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderExceptionTest {

    @Test
    void openRouter402_mapsToQuotaExceeded() {
        AiProviderException exception =
                AiProviderException.from("openrouter", 402, "{\"error\":\"payment required\"}");

        assertThat(exception.getCode()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(exception.getHttpStatus()).isEqualTo(402);
        assertThat(exception.getMessage()).doesNotContain("payment required");
        assertThat(exception.getTechnicalDetail()).contains("payment required");
    }

    @Test
    void rateLimit429_mapsToQuotaExceeded() {
        AiProviderException exception =
                AiProviderException.from("gemini", 429, "{\"error\":\"rate limit\"}");

        assertThat(exception.getCode()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(exception.getMessage()).doesNotContain("rate limit");
    }

    @Test
    void serverErrors_mapToProviderUnavailable() {
        for (int status : new int[]{500, 502, 503, 504}) {
            AiProviderException exception =
                    AiProviderException.from("openai", status, "{\"secret\":\"raw-provider-body\"}");

            assertThat(exception.getCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(exception.getHttpStatus()).isEqualTo(status);
            assertThat(exception.getMessage()).doesNotContain("raw-provider-body");
            assertThat(exception.getTechnicalDetail()).contains("raw-provider-body");
        }
    }

    @Test
    void authStatuses_mapToAuthFailed() {
        assertThat(AiProviderException.from("openai", 401, "{}" ).getCode())
                .isEqualTo("AUTH_FAILED");
        assertThat(AiProviderException.from("openai", 403, "{}" ).getCode())
                .isEqualTo("AUTH_FAILED");
    }

    @Test
    void modelError_mapsToInvalidModel() {
        AiProviderException exception =
                AiProviderException.from("openrouter", 400, "{\"error\":\"model not_found\"}");

        assertThat(exception.getCode()).isEqualTo("INVALID_MODEL");
        assertThat(exception.getMessage()).doesNotContain("model not_found");
    }

    @Test
    void genericClientError_mapsToRequestFailed() {
        AiProviderException exception =
                AiProviderException.from("gemini", 400, "{\"error\":\"bad request\"}");

        assertThat(exception.getCode()).isEqualTo("REQUEST_FAILED");
        assertThat(exception.getMessage()).isEqualTo("Gemini request failed (HTTP 400).");
        assertThat(exception.getTechnicalDetail()).contains("bad request");
    }
}
