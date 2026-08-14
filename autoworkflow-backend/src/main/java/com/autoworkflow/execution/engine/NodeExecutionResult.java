package com.autoworkflow.execution.engine;

import com.fasterxml.jackson.databind.JsonNode;

public record NodeExecutionResult(
        boolean success,
        JsonNode outputPayload,
        String error,
        Boolean branchTaken,  // for IF Condition / AI Router: which downstream edge label to follow (null = follow all)
        String branchKey      // for Switch: named branch to follow (null = not a named-branch result)
) {
    /** Backward-compatible 4-arg constructor for any call site built against the pre-Switch shape; branchKey defaults to null. */
    public NodeExecutionResult(boolean success, JsonNode outputPayload, String error, Boolean branchTaken) {
        this(success, outputPayload, error, branchTaken, null);
    }

    public static NodeExecutionResult ok(JsonNode output) {
        return new NodeExecutionResult(true, output, null, null, null);
    }

    public static NodeExecutionResult okWithBranch(JsonNode output, boolean branch) {
        return new NodeExecutionResult(true, output, null, branch, null);
    }

    /** For Switch: edge.data.branch is compared against this string, not parsed as a boolean. */
    public static NodeExecutionResult okWithBranchKey(JsonNode output, String branchKey) {
        return new NodeExecutionResult(true, output, null, null, branchKey);
    }

    public static NodeExecutionResult failed(String error) {
        return new NodeExecutionResult(false, null, error, null, null);
    }
}
