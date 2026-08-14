package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** MergeStrategy consumes the { "inputs": [...] } shape WorkflowExecutor builds for any multi-input node. */
class MergeStrategyTest {

    private final MergeStrategy strategy = new MergeStrategy();

    private NodeExecutionContext ctx(JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "merge-1", "merge", JsonUtils.mapper().createObjectNode(), input);
    }

    @Test
    void shallowMergesObjectInputsIntoOne() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"inputs\":[{\"a\":1},{\"b\":2}]}");
        NodeExecutionResult result = strategy.execute(ctx(input));

        assertThat(result.success()).isTrue();
        JsonNode merged = result.outputPayload().get("merged");
        assertThat(merged.get("a").asInt()).isEqualTo(1);
        assertThat(merged.get("b").asInt()).isEqualTo(2);
        assertThat(result.outputPayload().get("itemCount").asInt()).isEqualTo(2);
    }

    @Test
    void reportsKeyCollisionsRatherThanSilentlyOverwriting() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"inputs\":[{\"status\":\"a\"},{\"status\":\"b\"}]}");
        NodeExecutionResult result = strategy.execute(ctx(input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("collisions")).isNotNull();
        assertThat(result.outputPayload().get("collisions").get(0).asText()).isEqualTo("status");
    }

    @Test
    void nonObjectInputs_areReturnedAsRawArrayInsteadOfGuessing() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"inputs\":[[1,2],[3,4]]}");
        NodeExecutionResult result = strategy.execute(ctx(input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("merged").isArray()).isTrue();
    }

    @Test
    void singleIncomingEdge_passesThroughUnchanged() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"a\":1}");
        NodeExecutionResult result = strategy.execute(ctx(input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload()).isEqualTo(input);
    }
}
