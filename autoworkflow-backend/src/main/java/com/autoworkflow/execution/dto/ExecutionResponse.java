package com.autoworkflow.execution.dto;

import com.autoworkflow.execution.Execution;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        String status,
        String triggeredBy,
        Long durationMs,
        Instant startedAt,
        String errorMessage
) {
    public static ExecutionResponse from(Execution e) {
        return from(e, null);
    }

    public static ExecutionResponse from(Execution e, String workflowName) {
        return new ExecutionResponse(e.getId(), e.getWorkflowId(), workflowName, e.getStatus().name(),
                e.getTriggeredBy().name(), e.getDurationMs(), e.getStartedAt(), e.getErrorMessage());
    }
}
