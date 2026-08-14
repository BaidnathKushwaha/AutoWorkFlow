package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchStrategyTest {

    private final SwitchStrategy strategy = new SwitchStrategy();

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "switch", config, input);
    }

    private ObjectNode config(String field, String defaultCase, String... cases) {
        ObjectNode c = JsonUtils.mapper().createObjectNode();
        c.put("field", field);
        var arr = c.putArray("cases");
        for (String s : cases) arr.add(s);
        if (defaultCase != null) c.put("defaultCase", defaultCase);
        return c;
    }

    @Test
    void matchingCase_returnsThatCaseAsBranchKey() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"match\":\"Strong\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("match", "Weak", "Strong", "Moderate", "Weak"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.branchKey()).isEqualTo("Strong");
        assertThat(result.branchTaken()).isNull();
        assertThat(result.outputPayload()).isEqualTo(input); // passes original input through unchanged
    }

    @Test
    void noMatchingCase_fallsBackToDefaultCase() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"match\":\"Unknown\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("match", "Weak", "Strong", "Moderate", "Weak"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.branchKey()).isEqualTo("Weak");
    }

    @Test
    void missingFieldInPayload_fallsBackToDefaultCase() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"somethingElse\":\"x\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("match", "Weak", "Strong", "Moderate", "Weak"), input));

        assertThat(result.success()).isTrue();
        assertThat(result.branchKey()).isEqualTo("Weak");
    }

    @Test
    void missingFieldInPayload_noDefaultCase_failsClearly() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"somethingElse\":\"x\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("match", null, "Strong", "Moderate", "Weak"), input));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("no value for field");
    }

    @Test
    void noMatchingCase_andNoDefaultCase_failsClearly() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"match\":\"Unknown\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("match", null, "Strong", "Moderate", "Weak"), input));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("did not match any configured case");
    }

    @Test
    void missingFieldConfig_failsClearly() throws Exception {
        ObjectNode cfg = JsonUtils.mapper().createObjectNode();
        cfg.putArray("cases").add("Strong");
        JsonNode input = JsonUtils.mapper().readTree("{\"match\":\"Strong\"}");

        NodeExecutionResult result = strategy.execute(ctx(cfg, input));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("field");
    }

    @Test
    void missingCasesConfig_failsClearly() throws Exception {
        ObjectNode cfg = JsonUtils.mapper().createObjectNode();
        cfg.put("field", "match");
        JsonNode input = JsonUtils.mapper().readTree("{\"match\":\"Strong\"}");

        NodeExecutionResult result = strategy.execute(ctx(cfg, input));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("cases");
    }
}
