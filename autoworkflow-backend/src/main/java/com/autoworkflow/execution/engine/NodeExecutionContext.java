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
    private final JsonNode nodeConfig;   // the node's `data` from the React Flow canvas
    private final JsonNode inputPayload; // output of the upstream node (or trigger payload for the first node)

    public NodeExecutionContext(UUID userId, UUID workflowId, UUID executionId, String nodeId,
                                 String nodeType, JsonNode nodeConfig, JsonNode inputPayload) {
        this.userId = userId;
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.nodeConfig = nodeConfig;
        this.inputPayload = inputPayload;
    }
}
