package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class EmailReceivedTriggerStrategy implements NodeStrategy {
    @Override public String getTypeKey() { return "email_received"; }
    @Override public boolean isTrigger() { return true; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode input = ctx.getInputPayload();
        JsonNode config = ctx.getNodeConfig();
        String from = config.path("fromFilter").asText("").trim().toLowerCase();
        String subject = config.path("subjectFilter").asText("").trim().toLowerCase();
        String sender = input.path("sender").asText("").toLowerCase();
        String messageSubject = input.path("subject").asText("").toLowerCase();
        if ((!from.isBlank() && !sender.contains(from)) || (!subject.isBlank() && !messageSubject.contains(subject))) {
            return NodeExecutionResult.ok(input);
        }

        ObjectNode output = input != null && input.isObject()
                ? (ObjectNode) input.deepCopy()
                : com.autoworkflow.util.JsonUtils.mapper().createObjectNode();
        if (!config.path("jobDescription").asText("").isBlank()) {
            output.put("jobDescription", config.path("jobDescription").asText());
        }
        return NodeExecutionResult.ok(output);
    }
}
