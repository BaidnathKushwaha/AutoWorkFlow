package com.autoworkflow.common.exception;

public class NodeExecutionException extends RuntimeException {
    private final String nodeId;
    private final String nodeType;

    public NodeExecutionException(String nodeId, String nodeType, String message, Throwable cause) {
        super(message, cause);
        this.nodeId = nodeId;
        this.nodeType = nodeType;
    }

    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
}
