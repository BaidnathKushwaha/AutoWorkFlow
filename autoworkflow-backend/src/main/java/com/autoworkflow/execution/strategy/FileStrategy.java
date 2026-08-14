package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/**
 * Reads or writes a file under the workflow's isolated storage directory
 * (uploads/{workflowId}/...) so files never cross between workflows/users.
 */
@Component
public class FileStrategy implements NodeStrategy {

    private static final Path STORAGE_ROOT = Paths.get("uploads");

    @Override public String getTypeKey() { return "file"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        String action = config.path("action").asText("read");
        String fileName = config.path("fileName").asText();

        try {
            Path dir = STORAGE_ROOT.resolve(ctx.getWorkflowId().toString());
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName).normalize();

            if (!filePath.startsWith(dir)) {
                return NodeExecutionResult.failed("Invalid file path");
            }

            ObjectNode output = JsonUtils.mapper().createObjectNode();
            if ("write".equals(action)) {
                String content = config.path("content").asText(ctx.getInputPayload().toString());
                Files.writeString(filePath, content);
                output.put("written", true);
                output.put("path", filePath.toString());
            } else {
                if (!Files.exists(filePath)) {
                    return NodeExecutionResult.failed("File not found: " + fileName);
                }
                output.put("content", Files.readString(filePath));
            }
            return NodeExecutionResult.ok(output);
        } catch (IOException e) {
            return NodeExecutionResult.failed("File operation failed: " + e.getMessage());
        }
    }
}
