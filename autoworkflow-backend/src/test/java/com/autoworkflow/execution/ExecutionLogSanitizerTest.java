package com.autoworkflow.execution;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionLogSanitizerTest {

    @Test
    void sanitize_recursivelyRedactsCredentialFieldsAndPreservesSafeFields() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("""
                {
                  "name": "John",
                  "headers": {
                    "Authorization": "Bearer secret-value",
                    "authorization": "Bearer second-secret",
                    "Content-Type": "application/json"
                  },
                  "nested": {
                    "apiKey": "abc",
                    "refreshToken": "refresh-secret",
                    "password": "pw",
                    "client_secret": "client-secret",
                    "privateKey": "private-secret",
                    "safe": "visible"
                  },
                  "items": [
                    {"access_token": "oauth-secret", "value": 42},
                    {"cookie": "session-secret", "label": "kept"}
                  ]
                }
                """);

        JsonNode sanitized = ExecutionLogSanitizer.sanitize(input);

        assertThat(sanitized.get("name").asText()).isEqualTo("John");
        assertThat(sanitized.at("/headers/Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/headers/authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/headers/Content-Type").asText()).isEqualTo("application/json");
        assertThat(sanitized.at("/nested/apiKey").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/nested/refreshToken").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/nested/password").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/nested/client_secret").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/nested/privateKey").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/nested/safe").asText()).isEqualTo("visible");
        assertThat(sanitized.at("/items/0/access_token").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/items/0/value").asInt()).isEqualTo(42);
        assertThat(sanitized.at("/items/1/cookie").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.at("/items/1/label").asText()).isEqualTo("kept");
    }

    @Test
    void sanitize_doesNotMutateOriginalPayload() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"token\":\"secret\",\"value\":\"visible\"}");

        JsonNode sanitized = ExecutionLogSanitizer.sanitize(input);

        assertThat(input.get("token").asText()).isEqualTo("secret");
        assertThat(sanitized.get("token").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("value").asText()).isEqualTo("visible");
    }
}
