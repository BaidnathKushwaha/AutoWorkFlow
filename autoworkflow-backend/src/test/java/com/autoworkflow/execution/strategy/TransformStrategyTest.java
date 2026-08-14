package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase 27's required TransformStrategy scenarios: simple mapping, nested path,
 * array index, missing path, multiple output fields, empty mapping (passthrough).
 * Runnable without any network/API access — Transform never calls an external provider.
 */
class TransformStrategyTest {

    private final TransformStrategy strategy = new TransformStrategy();

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "transform", config, input);
    }

    private JsonNode mappingConfig(String... outputSourcePairs) {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        var mappings = config.putArray("mappings");
        for (int i = 0; i < outputSourcePairs.length; i += 2) {
            ObjectNode row = mappings.addObject();
            row.put("output", outputSourcePairs[i]);
            row.put("source", outputSourcePairs[i + 1]);
        }
        return config;
    }

    @Test
    void simpleMapping_extractsTopLevelField() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"status\":\"active\"}");
        NodeExecutionResult result = strategy.execute(ctx(mappingConfig("state", "status"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("state").asText()).isEqualTo("active");
    }

    @Test
    void nestedPath_resolvesThroughObjects() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree(
                "{\"repository\":{\"full_name\":\"org/repo\"},\"pusher\":{\"name\":\"baidnath\"}}");
        NodeExecutionResult result = strategy.execute(
                ctx(mappingConfig("repo", "repository.full_name", "pusher", "pusher.name"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("repo").asText()).isEqualTo("org/repo");
        assertThat(result.outputPayload().get("pusher").asText()).isEqualTo("baidnath");
    }

    @Test
    void arrayIndex_resolvesNumericSegment() throws Exception {
        // This is the exact bug the previous implementation had: JsonNode.path(String) does
        // NOT index into an ArrayNode, so "commits.0.message" silently resolved to null.
        JsonNode input = JsonUtils.mapper().readTree(
                "{\"commits\":[{\"id\":\"abc123\",\"message\":\"Add important note\"}]}");
        NodeExecutionResult result = strategy.execute(ctx(mappingConfig("message", "commits.0.message"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("message").asText()).isEqualTo("Add important note");
    }

    @Test
    void missingPath_resolvesToNullWithoutFailingTheNode() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"a\":1}");
        NodeExecutionResult result = strategy.execute(ctx(mappingConfig("b", "does.not.exist"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("b").isNull()).isTrue();
    }

    @Test
    void multipleOutputFields_allPresentInOrder() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree(
                "{\"repository\":{\"full_name\":\"a/b\"},\"ref\":\"refs/heads/main\",\"pusher\":{\"name\":\"p\"},"
                        + "\"commits\":[{\"message\":\"m\"}]}");
        NodeExecutionResult result = strategy.execute(ctx(mappingConfig(
                "repo", "repository.full_name",
                "branch", "ref",
                "pusher", "pusher.name",
                "message", "commits.0.message"), input));

        assertThat(result.success()).isTrue();
        ObjectNode out = (ObjectNode) result.outputPayload();
        assertThat(out.get("repo").asText()).isEqualTo("a/b");
        assertThat(out.get("branch").asText()).isEqualTo("refs/heads/main");
        assertThat(out.get("pusher").asText()).isEqualTo("p");
        assertThat(out.get("message").asText()).isEqualTo("m");
    }

    @Test
    void stripPrefix_removesLiteralPrefixFromStringValue() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        ObjectNode row = config.putArray("mappings").addObject();
        row.put("output", "branch");
        row.put("source", "ref");
        row.put("strip", "refs/heads/");

        JsonNode input = JsonUtils.mapper().readTree("{\"ref\":\"refs/heads/main\"}");
        NodeExecutionResult result = strategy.execute(ctx(config, input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("branch").asText()).isEqualTo("main");
    }

    @Test
    void emptyMapping_passesInputThroughUnchanged() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"anything\":\"here\"}");
        NodeExecutionResult result = strategy.execute(ctx(JsonUtils.mapper().createObjectNode(), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload()).isEqualTo(input);
    }

    @Test
    void malformedRow_missingSource_failsWithClearError() {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        ObjectNode row = config.putArray("mappings").addObject();
        row.put("output", "repo");
        // no "source" set

        NodeExecutionResult result = strategy.execute(ctx(config, JsonUtils.mapper().createObjectNode()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("repo");
    }

    @Test
    void legacyObjectMappingFormat_stillWorks() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.putObject("mapping").put("summary", "$.text");

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello\"}");
        NodeExecutionResult result = strategy.execute(ctx(config, input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("summary").asText()).isEqualTo("hello");
    }
}
