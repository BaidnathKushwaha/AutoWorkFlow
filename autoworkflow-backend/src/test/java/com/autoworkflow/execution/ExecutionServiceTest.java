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

/**
 * Phase 10: ExecutionService.execute() (used by manual Run, webhooks, and the cron
 * scheduler alike) must validate with validateForExecution — NOT validateForDeployment
 * — so a trigger-less workflow (e.g. a standalone Summarizer) can still run manually.
 * Also covers scenario H: execution history is never overwritten/deleted, only added to.
 */
class ExecutionServiceTest {

    private ExecutionRepository executionRepository;
    private WorkflowRepository workflowRepository;
    private WorkflowExecutor workflowExecutor;
    private WorkflowValidator workflowValidator;
    private ExecutionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID workflowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        executionRepository = mock(ExecutionRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        workflowValidator = mock(WorkflowValidator.class);
        service = new ExecutionService(executionRepository, workflowRepository, workflowExecutor, workflowValidator);

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
        verify(workflowValidator, never()).validateDeploymentOrThrow(any(), any());
        verify(workflowValidator, never()).validateOrThrow(any(), any());
    }

    @Test
    void webhookAndScheduleTriggeredExecutions_useDeploymentValidation()
            throws Exception {

        Workflow workflow =
                standaloneSummarizerWorkflow();

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(workflow));

        when(workflowExecutor.run(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(
                WorkflowExecutor.ExecutionRunResult.success(
                        List.of(),
                        JsonUtils.mapper()
                                .createObjectNode()
                )
        );

        service.execute(
                workflowId,
                TriggeredBy.WEBHOOK,
                JsonUtils.mapper()
                        .createObjectNode()
        );

        service.execute(
                workflowId,
                TriggeredBy.SCHEDULE,
                JsonUtils.mapper()
                        .createObjectNode()
        );

        verify(workflowValidator, times(2))
                .validateDeploymentOrThrow(
                        any(Workflow.class)
                );

        verify(workflowValidator, never())
                .validateExecutionOrThrow(
                        any(),
                        any()
                );
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
        // Two independent runs -> two persisted rows (save called for each: once to create
        // RUNNING, once to update to a final status — 2 executions x 2 saves = 4 total).
        verify(executionRepository, times(4)).save(any(Execution.class));
        verify(executionRepository, never()).delete(any());
        verify(executionRepository, never()).deleteById(any());
    }

    @Test
    void manualRunResult_recordsSuccessStatusAndStepsLogsCorrectly() throws Exception {
        Workflow workflow = standaloneSummarizerWorkflow();
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        LogStep step = new LogStep("node-1", "Summarizer", "success", Instant.now(), Instant.now(),
                JsonUtils.mapper().createObjectNode(), JsonUtils.mapper().createObjectNode(), null, 5L);
        when(workflowExecutor.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(WorkflowExecutor.ExecutionRunResult.success(List.of(step), JsonUtils.mapper().createObjectNode()));

        ExecutionResponse response = service.execute(workflowId, TriggeredBy.MANUAL, null);

        assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS.name());
        assertThat(response.triggeredBy()).isEqualTo(TriggeredBy.MANUAL.name());
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
}
