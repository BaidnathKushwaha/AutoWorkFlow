package com.autoworkflow.execution.engine;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.UUID;

/** Everything a NodeStrategy needs to execute a single node. */
@Getter
public class NodeExecutionContext {
    private final UUID userId;
    private final UUID workflowId;
    private final UUID executionId;
    private final String nodeId;
    private final String nodeType;
    private final JsonNode nodeConfig;
    private final JsonNode inputPayload;
    private final Integer iterationIndex;
    private final Integer iterationCount;
    private final String iterationId;
    private final String parentLoopNodeId;
    private final String branchPath;

    public NodeExecutionContext(UUID userId, UUID workflowId, UUID executionId, String nodeId,
                                String nodeType, JsonNode nodeConfig, JsonNode inputPayload) {
        this(userId, workflowId, executionId, nodeId, nodeType, nodeConfig, inputPayload,
                null, null, null, null, null);
    }

    public NodeExecutionContext(UUID userId, UUID workflowId, UUID executionId, String nodeId,
                                String nodeType, JsonNode nodeConfig, JsonNode inputPayload,
                                Integer iterationIndex, Integer iterationCount, String iterationId,
                                String parentLoopNodeId, String branchPath) {
        this.userId = userId;
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.nodeConfig = nodeConfig;
        this.inputPayload = inputPayload;
        this.iterationIndex = iterationIndex;
        this.iterationCount = iterationCount;
        this.iterationId = iterationId;
        this.parentLoopNodeId = parentLoopNodeId;
        this.branchPath = branchPath;
    }
}
