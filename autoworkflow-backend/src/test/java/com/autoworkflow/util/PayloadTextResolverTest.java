package com.autoworkflow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadTextResolverTest {

    @Test
    void inputTextInConfig_takesTopPriority() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("inputText", "Direct prompt text");

        JsonNode payload = JsonUtils.mapper().readTree("{\"text\":\"Payload text\"}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Direct prompt text");
    }

    @Test
    void inputTextWithTemplate_replacesInputPlaceholder() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("inputText", "Analyze: {{input}}");

        JsonNode payload = JsonUtils.mapper().readTree("{\"status\":\"ok\"}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).contains("Analyze: {\"status\":\"ok\"}");
    }

    @Test
    void textFieldDotPath_resolvesNestedField() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("textField", "user.profile.bio");

        JsonNode payload = JsonUtils.mapper().readTree("{\"user\":{\"profile\":{\"bio\":\"Developer\"}}}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Developer");
    }

    @Test
    void textFieldArrayIndex_resolvesCommitMessage() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("textField", "commits.0.message");

        JsonNode payload = JsonUtils.mapper().readTree("{\"commits\":[{\"message\":\"Fix bug\"}]}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Fix bug");
    }

    @Test
    void payloadText_resolvesStandardField() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        JsonNode payload = JsonUtils.mapper().readTree("{\"text\":\"Standard webhook text\"}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Standard webhook text");
    }

    @Test
    void standardCascadeFields_resolveMessageAndCommit() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        JsonNode payload = JsonUtils.mapper().readTree("{\"message\":\"Hello from message\"}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Hello from message");
    }

    @Test
    void standardCascadeFields_resolveCommitMessage() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        JsonNode payload = JsonUtils.mapper().readTree("{\"commits\":[{\"message\":\"Fix bug\"}]}");

        String resolved = PayloadTextResolver.resolveText(config, payload, false);
        assertThat(resolved).isEqualTo("Fix bug");
    }

    @Test
    void missingText_throwsWhenFallbackDisabled() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("textField", "missing.field");

        JsonNode payload = JsonUtils.mapper().readTree("{\"a\":1}");

        assertThatThrownBy(() -> PayloadTextResolver.resolveText(config, payload, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.field");
    }

    @Test
    void missingText_returnsRawJsonWhenFallbackEnabled() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        JsonNode payload = JsonUtils.mapper().readTree("{\"custom\":\"data\"}");

        String resolved = PayloadTextResolver.resolveTextOrRaw(config, payload);
        assertThat(resolved).isEqualTo("{\"custom\":\"data\"}");
    }
}
