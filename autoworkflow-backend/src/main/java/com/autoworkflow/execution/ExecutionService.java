package com.autoworkflow.execution;

import com.autoworkflow.common.enums.ExecutionStatus;
import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.common.response.PageResponse;
import com.autoworkflow.execution.dto.ExecutionDetailResponse;
import com.autoworkflow.execution.dto.ExecutionResponse;
import com.autoworkflow.execution.engine.LogStep;
import com.autoworkflow.execution.engine.WorkflowExecutor;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

        private final ExecutionRepository executionRepository;
        private final WorkflowRepository workflowRepository;
        private final WorkflowExecutor workflowExecutor;
        private final com.autoworkflow.execution.validation.WorkflowValidator workflowValidator;

        /**
         * Runs a workflow synchronously end-to-end and persists the result.
         * Called from: manual "Run"/"trigger" button, webhook receiver, and the cron
         * scheduler.
         */
        @Transactional
        public ExecutionResponse execute(UUID workflowId, TriggeredBy triggeredBy, JsonNode triggerPayload) {
                Workflow workflow = workflowRepository.findById(workflowId)
                                .orElseThrow(() -> ResourceNotFoundException.of("Workflow", workflowId));

                if (triggeredBy == TriggeredBy.MANUAL) {
                        workflowValidator.validateExecutionOrThrow(
                                        workflow.getCanvasNodes(),
                                        workflow.getCanvasEdges());
                } else {
                        workflowValidator.validateDeploymentOrThrow(workflow);
                }

                Execution execution = Execution.builder()
                                .workflowId(workflow.getId())
                                .userId(workflow.getUserId())
                                .status(ExecutionStatus.RUNNING)
                                .triggeredBy(triggeredBy)
                                .stepsLogs(JsonUtils.mapper().createArrayNode())
                                .startedAt(Instant.now())
                                .build();
                execution = executionRepository.save(execution);

                Instant start = Instant.now();
                JsonNode payload = triggerPayload != null ? triggerPayload : JsonUtils.mapper().createObjectNode();

                WorkflowExecutor.ExecutionRunResult result = workflowExecutor.run(
                                workflow.getUserId(), workflow.getId(), execution.getId(),
                                workflow.getCanvasNodes(), workflow.getCanvasEdges(), payload);

                long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

                execution.setStatus(result.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
                execution.setStepsLogs(stepsToJson(result.steps()));
                execution.setErrorMessage(result.error());
                execution.setDurationMs(durationMs);
                execution.setFinishedAt(Instant.now());
                execution = executionRepository.save(execution);

                workflowRepository.incrementExecutionCount(workflow.getId(), Instant.now());

                return ExecutionResponse.from(execution, workflow.getName());
        }

        private JsonNode stepsToJson(List<LogStep> steps) {
                var array = JsonUtils.mapper().createArrayNode();
                steps.forEach(step -> array.add(JsonUtils.mapper().valueToTree(step)));
                return array;
        }

        public PageResponse<ExecutionResponse> listForUser(UUID userId, int page, int size) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
                return new PageResponse<>(executionRepository.findByUserIdOrderByStartedAtDesc(userId, pageable)
                                .map(e -> {
                                        String wfName = workflowRepository.findById(e.getWorkflowId())
                                                        .map(Workflow::getName)
                                                        .orElse("Unknown Workflow");
                                        return ExecutionResponse.from(e, wfName);
                                }));
        }

        public PageResponse<ExecutionResponse> listForWorkflow(UUID workflowId, int page, int size) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
                String wfName = workflowRepository.findById(workflowId)
                                .map(Workflow::getName)
                                .orElse("Unknown Workflow");
                return new PageResponse<>(executionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId, pageable)
                                .map(e -> ExecutionResponse.from(e, wfName)));
        }

        public ExecutionDetailResponse getDetail(UUID userId, UUID executionId) {
                Execution execution = executionRepository.findByIdAndUserId(executionId, userId)
                                .orElseThrow(() -> ResourceNotFoundException.of("Execution", executionId));
                String wfName = workflowRepository.findById(execution.getWorkflowId())
                                .map(Workflow::getName)
                                .orElse("Unknown Workflow");
                return ExecutionDetailResponse.from(execution, wfName);
        }
}
