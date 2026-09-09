package com.autoworkflow.execution;

import com.autoworkflow.common.enums.ExecutionStatus;
import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.execution.dto.ExecutionResponse;
import com.autoworkflow.execution.engine.LogStep;
import com.autoworkflow.execution.engine.WorkflowExecutor;
import com.autoworkflow.execution.validation.WorkflowValidator;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExecutionServiceTest {

    private ExecutionRepository executionRepository;
    private WorkflowRepository workflowRepository;
    private WorkflowExecutor workflowExecutor;
    private WorkflowValidator workflowValidator;
    private ExecutionFinalizationService executionFinalizationService;
    private ExecutionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID workflowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        executionRepository = mock(ExecutionRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        workflowValidator = mock(WorkflowValidator.class);
        executionFinalizationService = mock(ExecutionFinalizationService.class);
        service = new ExecutionService(
                executionRepository,
                workflowRepository,
                workflowExecutor,
                workflowValidator,
                executionFinalizationService
        );

        when(executionRepository.save(any(Execution.class))).thenAnswer(inv -> {
            Execution e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    private Workflow standaloneSummarizerWorkflow() throws Exception {
        JsonNode nodes = JsonUtils.mapper().readTree(
                "[{\"id\":\"node-1\",\"type\":\"summarizer\",\"data\":{\"inputText\":\"manual test text\"}}]");
        return Workflow.builder()
                .id(workflowId).userId(userId).name("Standalone Summarizer")
                .canvasNodes(nodes).canvasEdges(JsonUtils.mapper().createArrayNode())
                .build();
    }

    @Test
    void manualExecution_usesExecutionModeValidation_notDeploymentMode() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(List.of(), JsonUtils.mapper().createObjectNode()));

        service.execute(workflowId, TriggeredBy.MANUAL, null);

        verify(workflowValidator).validateExecutionOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges());
        verify(workflowValidator, never()).validateDeploymentOrThrow(any(Workflow.class));
        verify(workflowValidator, never()).validateOrThrow(any(), any());
    }

    @Test
    void webhookAndScheduleTriggeredExecutions_useDeploymentValidation() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(
                        List.of(), JsonUtils.mapper().createObjectNode()));

        service.execute(workflowId, TriggeredBy.WEBHOOK, JsonUtils.mapper().createObjectNode());
        service.execute(workflowId, TriggeredBy.SCHEDULE, JsonUtils.mapper().createObjectNode());

        verify(workflowValidator, times(2)).validateDeploymentOrThrow(any(Workflow.class));
        verify(workflowValidator, never()).validateExecutionOrThrow(any(), any());
    }

    @Test
    void everyExecuteCall_createsANewExecutionRecord_neverDeletesOrOverwritesPriorOnes() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(
                        List.of(), JsonUtils.mapper().createObjectNode()));

        ExecutionResponse first = service.execute(workflowId, TriggeredBy.MANUAL, null);
        ExecutionResponse second = service.execute(workflowId, TriggeredBy.MANUAL, null);

        assertThat(first.id()).isNotEqualTo(second.id());
        verify(executionRepository, times(4)).save(any(Execution.class));
        verify(executionRepository, never()).delete(any());
        verify(executionRepository, never()).deleteById(any());
        verify(workflowRepository, times(2)).incrementExecutionCount(eq(workflowId), any(Instant.class));
    }

    @Test
    void manualRunResult_recordsSuccessStatusAndTiming() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        LogStep step = new LogStep("node-1", "Summarizer", "success", Instant.now(), Instant.now(),
                JsonUtils.mapper().createObjectNode(), JsonUtils.mapper().createObjectNode(), null, 5L);
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(List.of(step), JsonUtils.mapper().createObjectNode()));

        ExecutionResponse response = service.execute(workflowId, TriggeredBy.MANUAL, null);

        assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS.name());
        assertThat(response.triggeredBy()).isEqualTo(TriggeredBy.MANUAL.name());
        assertThat(response.durationMs()).isNotNull();
    }

    @Test
    void executionStepPayloads_areSanitizedBeforePersistence() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        JsonNode input = JsonUtils.mapper().readTree("{\"name\":\"John\",\"headers\":{\"Authorization\":\"Bearer abc\",\"Content-Type\":\"application/json\"},\"apiKey\":\"key\"}");
        JsonNode output = JsonUtils.mapper().readTree("{\"token\":\"oauth-token\",\"result\":\"safe\"}");
        LogStep step = new LogStep("node-1", "HTTP", "success", Instant.now(), Instant.now(),
                input, output, null, 5L);
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(List.of(step), output));

        service.execute(workflowId, TriggeredBy.MANUAL, null);

        var captor = org.mockito.ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, times(2)).save(captor.capture());
        JsonNode persistedSteps = captor.getAllValues().get(1).getStepsLogs();

        assertThat(persistedSteps.at("/0/inputPayload/headers/Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(persistedSteps.at("/0/inputPayload/headers/Content-Type").asText()).isEqualTo("application/json");
        assertThat(persistedSteps.at("/0/inputPayload/apiKey").asText()).isEqualTo("[REDACTED]");
        assertThat(persistedSteps.at("/0/outputPayload/token").asText()).isEqualTo("[REDACTED]");
        assertThat(persistedSteps.at("/0/outputPayload/result").asText()).isEqualTo("safe");
    }

    @Test
    void manualRunFailure_recordsFailedStatus_doesNotThrowRawException() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.failed(List.of(), "Node 'Summarizer' failed: no text found"));

        ExecutionResponse response = service.execute(workflowId, TriggeredBy.MANUAL, null);

        assertThat(response.status()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(response.errorMessage()).contains("no text found");
    }

    @Test
    void unexpectedExecutorException_finalizesAsFailedWithSafeError() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("provider secret and stack trace should not escape"));

        ExecutionResponse response = service.execute(workflowId, TriggeredBy.MANUAL, null);

        assertThat(response.status()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(response.errorMessage()).isEqualTo("Workflow execution failed unexpectedly.");
        assertThat(response.errorMessage()).doesNotContain("secret");
        assertThat(response.durationMs()).isNotNull();
        verify(executionFinalizationService).markFailedBestEffort(
                any(Execution.class), anyLong(), eq("Workflow execution failed unexpectedly."));
        verify(workflowRepository).incrementExecutionCount(eq(workflowId), any(Instant.class));
    }
}
