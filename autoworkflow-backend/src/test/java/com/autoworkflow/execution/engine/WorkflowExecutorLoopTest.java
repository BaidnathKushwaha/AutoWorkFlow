package com.autoworkflow.execution.engine;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowExecutorLoopTest {
    @Test
    void executesBodyOncePerItemAndContinuationAfterAllIterations() throws Exception {
        NodeStrategyRegistry registry = mock(NodeStrategyRegistry.class);
        NodeStrategy trigger = context -> NodeExecutionResult.ok(context.getInputPayload());
        NodeStrategy loop = context -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("items", context.getInputPayload().path("items").deepCopy());
            out.put("count", context.getInputPayload().path("items").size());
            return NodeExecutionResult.ok(out);
        };
        NodeStrategy body = context -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("item", context.getInputPayload().path("item").deepCopy());
            out.put("index", context.getIterationIndex());
            out.put("count", context.getIterationCount());
            return NodeExecutionResult.ok(out);
        };
        NodeStrategy continuation = context -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("results", context.getInputPayload().path("results").deepCopy());
            out.put("done", true);
            return NodeExecutionResult.ok(out);
        };
        when(registry.resolve("trigger")).thenReturn(trigger);
        when(registry.resolve("loop")).thenReturn(loop);
        when(registry.resolve("body")).thenReturn(body);
        when(registry.resolve("continuation")).thenReturn(continuation);

        WorkflowExecutor executor = new WorkflowExecutor(registry);
        JsonNode nodes = JsonUtils.mapper().readTree("[" +
                "{\"id\":\"t\",\"type\":\"trigger\",\"data\":{}}," +
                "{\"id\":\"l\",\"type\":\"loop\",\"data\":{\"arrayField\":\"items\",\"bodyStartNodeId\":\"b\",\"continuationNodeId\":\"c\"}}," +
                "{\"id\":\"b\",\"type\":\"body\",\"data\":{}}," +
                "{\"id\":\"c\",\"type\":\"continuation\",\"data\":{}}]");
        JsonNode edges = JsonUtils.mapper().readTree("[" +
                "{\"source\":\"t\",\"target\":\"l\"}," +
                "{\"source\":\"l\",\"target\":\"b\",\"data\":{\"branch\":\"body\",\"loopRole\":\"body\"}}," +
                "{\"source\":\"l\",\"target\":\"c\",\"data\":{\"branch\":\"continuation\",\"loopRole\":\"continuation\"}}," +
                "{\"source\":\"b\",\"target\":\"c\",\"data\":{\"loopReturn\":true}}]");
        JsonNode input = JsonUtils.mapper().readTree("{\"items\":[{\"id\":1},{\"id\":2},{\"id\":3}]}");

        WorkflowExecutor.ExecutionRunResult result = executor.run(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nodes, edges, input);

        assertThat(result.success()).isTrue();
        assertThat(result.finalOutput().path("done").asBoolean()).isTrue();
        assertThat(result.finalOutput().path("results")).hasSize(3);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).hasSize(3);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).extracting(LogStep::getIterationIndex).containsExactly(0, 1, 2);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).extracting(LogStep::getIterationCount).containsOnly(3);
        assertThat(result.steps()).filteredOn(s -> "c".equals(s.getNodeId())).hasSize(1);
    }

    @Test
    void emptyCollectionRunsZeroBodyIterationsAndStillRunsContinuation() throws Exception {
        NodeStrategyRegistry registry = mock(NodeStrategyRegistry.class);
        when(registry.resolve("trigger")).thenReturn(c -> NodeExecutionResult.ok(c.getInputPayload()));
        when(registry.resolve("loop")).thenReturn(c -> {
            var out = JsonUtils.mapper().createObjectNode(); out.set("items", c.getInputPayload().path("items")); out.put("count", 0); return NodeExecutionResult.ok(out);
        });
        when(registry.resolve("continuation")).thenReturn(c -> NodeExecutionResult.ok(c.getInputPayload()));
        WorkflowExecutor executor = new WorkflowExecutor(registry);
        JsonNode nodes = JsonUtils.mapper().readTree("[{\"id\":\"t\",\"type\":\"trigger\",\"data\":{}},{\"id\":\"l\",\"type\":\"loop\",\"data\":{\"arrayField\":\"items\",\"bodyStartNodeId\":\"b\",\"continuationNodeId\":\"c\"}},{\"id\":\"b\",\"type\":\"body\",\"data\":{}},{\"id\":\"c\",\"type\":\"continuation\",\"data\":{}}]");
        JsonNode edges = JsonUtils.mapper().readTree("[{\"source\":\"t\",\"target\":\"l\"},{\"source\":\"l\",\"target\":\"b\",\"data\":{\"loopRole\":\"body\",\"branch\":\"body\"}},{\"source\":\"l\",\"target\":\"c\",\"data\":{\"loopRole\":\"continuation\",\"branch\":\"continuation\"}},{\"source\":\"b\",\"target\":\"c\",\"data\":{\"loopReturn\":true}}]");
        WorkflowExecutor.ExecutionRunResult result = executor.run(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nodes, edges, JsonUtils.mapper().readTree("{\"items\":[]}"));
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).noneMatch(s -> "b".equals(s.getNodeId()));
        assertThat(result.steps()).filteredOn(s -> "c".equals(s.getNodeId())).hasSize(1);
    }
}
