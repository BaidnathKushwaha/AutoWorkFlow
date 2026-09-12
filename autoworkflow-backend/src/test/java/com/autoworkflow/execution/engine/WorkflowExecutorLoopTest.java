package com.autoworkflow.execution.engine;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowExecutorLoopTest {
    private static NodeStrategy strategy(String type, Function<NodeExecutionContext, NodeExecutionResult> fn) {
        return new NodeStrategy() {
            @Override public String getTypeKey() { return type; }
            @Override public NodeExecutionResult execute(NodeExecutionContext context) { return fn.apply(context); }
        };
    }

    @Test
    void executesBodyOncePerItemAndContinuationAfterAllIterations() throws Exception {
        NodeStrategyRegistry registry = mock(NodeStrategyRegistry.class);
        NodeStrategy trigger = strategy("trigger", c -> NodeExecutionResult.ok(c.getInputPayload()));
        NodeStrategy loop = strategy("loop", c -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("items", c.getInputPayload().path("items").deepCopy());
            out.put("count", c.getInputPayload().path("items").size());
            return NodeExecutionResult.ok(out);
        });
        NodeStrategy body = strategy("body", c -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("item", c.getInputPayload().path("item").deepCopy());
            out.put("index", c.getIterationIndex());
            out.put("count", c.getIterationCount());
            return NodeExecutionResult.ok(out);
        });
        NodeStrategy continuation = strategy("continuation", c -> {
            var out = JsonUtils.mapper().createObjectNode();
            out.set("results", c.getInputPayload().path("results").deepCopy());
            out.put("done", true);
            return NodeExecutionResult.ok(out);
        });
        when(registry.resolve("trigger")).thenReturn(trigger);
        when(registry.resolve("loop")).thenReturn(loop);
        when(registry.resolve("body")).thenReturn(body);
        when(registry.resolve("continuation")).thenReturn(continuation);

        WorkflowExecutor executor = new WorkflowExecutor(registry);
        JsonNode nodes = JsonUtils.mapper().readTree("[{" +
                "\"id\":\"t\",\"type\":\"trigger\",\"data\":{}},{" +
                "\"id\":\"l\",\"type\":\"loop\",\"data\":{\"arrayField\":\"items\",\"bodyStartNodeId\":\"b\",\"continuationNodeId\":\"c\"}},{" +
                "\"id\":\"b\",\"type\":\"body\",\"data\":{}},{" +
                "\"id\":\"c\",\"type\":\"continuation\",\"data\":{}}]");
        JsonNode edges = JsonUtils.mapper().readTree("[{" +
                "\"source\":\"t\",\"target\":\"l\"},{" +
                "\"source\":\"l\",\"target\":\"b\",\"data\":{\"branch\":\"body\",\"loopRole\":\"body\"}},{" +
                "\"source\":\"l\",\"target\":\"c\",\"data\":{\"branch\":\"continuation\",\"loopRole\":\"continuation\"}},{" +
                "\"source\":\"b\",\"target\":\"c\",\"data\":{\"loopReturn\":true}}]");
        JsonNode input = JsonUtils.mapper().readTree("{\"items\":[{\"id\":1},{\"id\":2},{\"id\":3}]}");

        WorkflowExecutor.ExecutionRunResult result = executor.run(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nodes, edges, input);

        assertThat(result.success()).isTrue();
        assertThat(result.finalOutput().path("done").asBoolean()).isTrue();
        assertThat(result.finalOutput().path("results").isArray()).isTrue();
        assertThat(result.finalOutput().path("results").size()).isEqualTo(3);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).hasSize(3);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).extracting(LogStep::getIterationIndex).containsExactly(0, 1, 2);
        assertThat(result.steps()).filteredOn(s -> "b".equals(s.getNodeId())).extracting(LogStep::getIterationCount).containsOnly(3);
        assertThat(result.steps()).filteredOn(s -> "c".equals(s.getNodeId())).hasSize(1);
    }

    @Test
    void emptyCollectionRunsZeroBodyIterationsAndStillRunsContinuation() throws Exception {
        NodeStrategyRegistry registry = mock(NodeStrategyRegistry.class);
        when(registry.resolve("trigger")).thenReturn(strategy("trigger", c -> NodeExecutionResult.ok(c.getInputPayload())));
        when(registry.resolve("loop")).thenReturn(strategy("loop", c -> {
            var out = JsonUtils.mapper().createObjectNode(); out.set("items", c.getInputPayload().path("items")); out.put("count", 0); return NodeExecutionResult.ok(out);
        }));
        when(registry.resolve("continuation")).thenReturn(strategy("continuation", c -> NodeExecutionResult.ok(c.getInputPayload())));
        WorkflowExecutor executor = new WorkflowExecutor(registry);
        JsonNode nodes = JsonUtils.mapper().readTree("[{\"id\":\"t\",\"type\":\"trigger\",\"data\":{}},{\"id\":\"l\",\"type\":\"loop\",\"data\":{\"arrayField\":\"items\",\"bodyStartNodeId\":\"b\",\"continuationNodeId\":\"c\"}},{\"id\":\"b\",\"type\":\"body\",\"data\":{}},{\"id\":\"c\",\"type\":\"continuation\",\"data\":{}}]");
        JsonNode edges = JsonUtils.mapper().readTree("[{\"source\":\"t\",\"target\":\"l\"},{\"source\":\"l\",\"target\":\"b\",\"data\":{\"loopRole\":\"body\",\"branch\":\"body\"}},{\"source\":\"l\",\"target\":\"c\",\"data\":{\"loopRole\":\"continuation\",\"branch\":\"continuation\"}},{\"source\":\"b\",\"target\":\"c\",\"data\":{\"loopReturn\":true}}]");
        WorkflowExecutor.ExecutionRunResult result = executor.run(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nodes, edges, JsonUtils.mapper().readTree("{\"items\":[]}"));
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).noneMatch(s -> "b".equals(s.getNodeId()));
        assertThat(result.steps()).filteredOn(s -> "c".equals(s.getNodeId())).hasSize(1);
    }
}
