package com.autoworkflow.execution.engine;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** Mirrors the frontend's LogStep interface exactly so Execution Detail renders without transformation. */
@Getter
@AllArgsConstructor
public class LogStep {
    private final String nodeId;
    private final String nodeName;
    private final String status;         // success | running | failed | pending
    private final Instant startTime;
    private final Instant endTime;
    private final JsonNode inputPayload;
    private final JsonNode outputPayload;
    private final String error;
    private final Long durationMs;
}
