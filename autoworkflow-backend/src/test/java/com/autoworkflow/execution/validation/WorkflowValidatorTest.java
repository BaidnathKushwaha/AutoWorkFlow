package com.autoworkflow.execution.validation;

import com.autoworkflow.execution.engine.NodeStrategyRegistry;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class WorkflowValidatorTest {

    private NodeStrategyRegistry registry;
    private WorkflowValidator validator;

    @BeforeEach
    void setUp() {
        registry = Mockito.mock(NodeStrategyRegistry.class);
        validator = new WorkflowValidator(registry);

        when(registry.isRegisteredType("webhook")).thenReturn(true);
        when(registry.isTriggerType("webhook")).thenReturn(true);

        when(registry.isRegisteredType("ai")).thenReturn(true);
        when(registry.isTriggerType("ai")).thenReturn(false);

        when(registry.isRegisteredType("summarizer")).thenReturn(true);
        when(registry.isTriggerType("summarizer")).thenReturn(false);
    }

    @Test
    void noNodes_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ArrayNode edges = JsonUtils.mapper().createArrayNode();

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("contains no nodes");
    }

    @Test
    void nodeWithoutId_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode node = nodes.addObject();
        node.put("type", "webhook");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("missing or blank ID");
    }

    @Test
    void duplicateNodeIds_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");

        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-1");
        n2.put("type", "ai");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("Duplicate node ID");
    }

    @Test
    void unknownNodeType_failsValidation() {
        when(registry.isRegisteredType("unknown_type")).thenReturn(false);

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "unknown_type");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("Unknown or unregistered node type");
    }

    @Test
    void edgeWithMissingSource_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("target", "node-1");

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("missing or blank source");
    }

    @Test
    void edgeWithMissingTarget_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("missing or blank target");
    }

    @Test
    void noTrigger_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "ai");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("no trigger node");
    }

    // --- Phase 10: execution-mode vs deployment-mode split ---
    // validate()/validateOrThrow() (above/existing) keep their old strict (= deployment)
    // behavior unchanged for any pre-existing caller. These new tests cover the two
    // explicit entry points ExecutionService and WorkflowService.deploy() now use.

    @Test
    void standaloneSummarizer_noTrigger_passesExecutionValidation() {
        // Scenario A: a lone Summarizer node (e.g. configured with Direct Input Text,
        // no upstream trigger needed) must be manually runnable.
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "summarizer");

        WorkflowValidationResult res = validator.validateForExecution(nodes, JsonUtils.mapper().createArrayNode());
        assertThat(res.isValid()).isTrue();
    }

    @Test
    void standaloneSummarizer_noTrigger_failsDeploymentValidation() {
        // Scenario B: that same workflow must NOT be deployable.
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "summarizer");

        WorkflowValidationResult res = validator.validateForDeployment(nodes, JsonUtils.mapper().createArrayNode());
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("no trigger node");
    }

    @Test
    void webhookThenSummarizer_passesExecutionValidation() {
        // Scenario C: a workflow that DOES have a trigger must still validate fine for
        // manual execution — the relaxed mode only removes a requirement, it never adds one.
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");
        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-2");
        n2.put("type", "summarizer");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");
        e1.put("target", "node-2");

        assertThat(validator.validateForExecution(nodes, edges).isValid()).isTrue();
    }

    @Test
    void webhookThenSummarizer_passesDeploymentValidation() {
        // Scenario D.
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");
        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-2");
        n2.put("type", "summarizer");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");
        e1.put("target", "node-2");

        assertThat(validator.validateForDeployment(nodes, edges).isValid()).isTrue();
    }

    @Test
    void cyclicWorkflow_rejectedInBothExecutionAndDeploymentModes() {
        // Scenario E: cycle detection is COMMON validation, must apply to both modes.
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");
        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-2");
        n2.put("type", "ai");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");
        e1.put("target", "node-2");
        ObjectNode e2 = edges.addObject();
        e2.put("source", "node-2");
        e2.put("target", "node-1");

        assertThat(validator.validateForExecution(nodes, edges).isValid()).isFalse();
        assertThat(validator.validateForExecution(nodes, edges).error()).contains("contains a cycle");
        assertThat(validator.validateForDeployment(nodes, edges).isValid()).isFalse();
        assertThat(validator.validateForDeployment(nodes, edges).error()).contains("contains a cycle");
    }

    @Test
    void invalidNodeType_rejectedInBothExecutionAndDeploymentModes() {
        // Scenario F: unregistered node type is also COMMON validation.
        when(registry.isRegisteredType("totally_fake_type")).thenReturn(false);

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "totally_fake_type");

        assertThat(validator.validateForExecution(nodes, JsonUtils.mapper().createArrayNode()).isValid()).isFalse();
        assertThat(validator.validateForDeployment(nodes, JsonUtils.mapper().createArrayNode()).isValid()).isFalse();
    }

    @Test
    void validateExecutionOrThrow_doesNotThrow_forStandaloneSummarizer() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "summarizer");

        validator.validateExecutionOrThrow(nodes, JsonUtils.mapper().createArrayNode()); // must not throw
    }

    @Test
    void validateDeploymentOrThrow_throws_forStandaloneSummarizer() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "summarizer");

        org.junit.jupiter.api.Assertions.assertThrows(
                com.autoworkflow.common.exception.WorkflowException.class,
                () -> validator.validateDeploymentOrThrow(nodes, JsonUtils.mapper().createArrayNode()));
    }

    @Test
    void validWorkflow_passesValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");

        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-2");
        n2.put("type", "ai");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");
        e1.put("target", "node-2");

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isTrue();
    }

    @Test
    void cycleInWorkflow_failsValidation() {
        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        ObjectNode n1 = nodes.addObject();
        n1.put("id", "node-1");
        n1.put("type", "webhook");

        ObjectNode n2 = nodes.addObject();
        n2.put("id", "node-2");
        n2.put("type", "ai");

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        ObjectNode e1 = edges.addObject();
        e1.put("source", "node-1");
        e1.put("target", "node-2");

        ObjectNode e2 = edges.addObject();
        e2.put("source", "node-2");
        e2.put("target", "node-1");

        WorkflowValidationResult res = validator.validate(nodes, edges);
        assertThat(res.isValid()).isFalse();
        assertThat(res.error()).contains("contains a cycle");
        assertThat(res.error()).contains("Cyclic execution is not supported");
    }
}
