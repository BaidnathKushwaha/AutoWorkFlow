package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailReceivedTriggerStrategyTest {

    @Test
    void preservesExistingJobDescriptionWhileKeepingExtractedBody() {
        EmailReceivedTriggerStrategy strategy = new EmailReceivedTriggerStrategy();
        JsonNode input = JsonUtils.mapper().createObjectNode()
                .put("sender", "candidate@example.com")
                .put("subject", "Application for Java Backend Developer")
                .put("body", "John Doe Java Spring Boot PostgreSQL")
                .put("jobDescription", "Java Backend Developer with Spring Boot");
        JsonNode config = JsonUtils.mapper().createObjectNode();

        NodeExecutionResult result = strategy.execute(new NodeExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "gmail", "email_received", config, input));

        assertTrue(result.success());
        assertEquals("John Doe Java Spring Boot PostgreSQL", result.outputPayload().path("body").asText());
        assertEquals("Java Backend Developer with Spring Boot", result.outputPayload().path("jobDescription").asText());
    }

    @Test
    void addsConfiguredJobDescriptionWhenIncomingValueIsMissing() {
        EmailReceivedTriggerStrategy strategy = new EmailReceivedTriggerStrategy();
        JsonNode input = JsonUtils.mapper().createObjectNode()
                .put("sender", "candidate@example.com")
                .put("subject", "Application")
                .put("body", "Resume text from attachment");
        JsonNode config = JsonUtils.mapper().createObjectNode()
                .put("jobDescription", "Java Backend Developer");

        NodeExecutionResult result = strategy.execute(new NodeExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "gmail", "email_received", config, input));

        assertEquals("Resume text from attachment", result.outputPayload().path("body").asText());
        assertEquals("Java Backend Developer", result.outputPayload().path("jobDescription").asText());
    }
}
