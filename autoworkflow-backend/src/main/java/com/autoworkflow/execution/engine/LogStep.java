package com.autoworkflow.execution.engine;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** Mirrors the frontend LogStep shape and carries optional scoped execution context. */
@Getter
@AllArgsConstructor
public class LogStep {
    private final String nodeId;
    private final String nodeName;
    private final String status;
    private final Instant startTime;
    private final Instant endTime;
    private final JsonNode inputPayload;
    private final JsonNode outputPayload;
    private final String error;
    private final Long durationMs;
    private final Integer iterationIndex;
    private final Integer iterationCount;
    private final String iterationId;
    private final String parentLoopNodeId;
    private final String branchPath;

    public LogStep(String nodeId, String nodeName, String status, Instant startTime, Instant endTime,
                   JsonNode inputPayload, JsonNode outputPayload, String error, Long durationMs) {
        this(nodeId, nodeName, status, startTime, endTime, inputPayload, outputPayload, error, durationMs,
                null, null, null, null, null);
    }
}
