package com.autoworkflow.execution.dto;

import com.autoworkflow.execution.Execution;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ExecutionDetailResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        String status,
        String triggeredBy,
        Long durationMs,
        JsonNode stepsLogs,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public static ExecutionDetailResponse from(Execution e) {
        return from(e, null);
    }

    public static ExecutionDetailResponse from(Execution e, String workflowName) {
        return new ExecutionDetailResponse(e.getId(), e.getWorkflowId(), workflowName, e.getStatus().name(),
                e.getTriggeredBy().name(), e.getDurationMs(), e.getStepsLogs(), e.getErrorMessage(),
                e.getStartedAt(), e.getFinishedAt());
    }
}
