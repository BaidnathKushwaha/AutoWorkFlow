package com.autoworkflow.execution.engine;

import com.autoworkflow.execution.strategy.EmailReceivedTriggerStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the real Email Received -> downstream AI execution contract.
 * The trigger strategy is real; only the AI node is a deterministic test double.
 */
class EmailResumeMatcherExecutionPathTest {

    @Test
    void gmailPayloadAndTriggerConfigurationReachDownstreamAiTogether() {
        EmailReceivedTriggerStrategy emailTrigger = new EmailReceivedTriggerStrategy();
        AtomicReference<JsonNode> aiInput = new AtomicReference<>();

        NodeStrategy captureAiInput = new NodeStrategy() {
            @Override
            public String getTypeKey() {
                return "ai";
            }

            @Override
            public NodeExecutionResult execute(NodeExecutionContext ctx) {
                aiInput.set(ctx.getInputPayload());
                return NodeExecutionResult.ok(ctx.getInputPayload());
            }
        };

        WorkflowExecutor executor = new WorkflowExecutor(
                new NodeStrategyRegistry(List.of(emailTrigger, captureAiInput)));

        ArrayNode nodes = JsonUtils.mapper().createArrayNode();
        nodes.add(node("email", "email_received", JsonUtils.mapper().createObjectNode()
                .put("label", "Resume Email")
                .put("subjectFilter", "Application")
                .put("jobDescription", "We are hiring a Java Backend Developer. Required skills: Java, Spring Boot, REST APIs, PostgreSQL. Git and Docker are preferred.")));
        nodes.add(node("matcher", "ai", JsonUtils.mapper().createObjectNode()
                .put("label", "Resume Matcher")
                .put("prompt", "Match the resume in {{body}} against this job description: {{jobDescription}}.")));

        ArrayNode edges = JsonUtils.mapper().createArrayNode();
        edges.add(edge("email", "matcher"));

        JsonNode gmailPayload = JsonUtils.mapper().createObjectNode()
                .put("messageId", "gmail-message-1")
                .put("sender", "candidate@example.com")
                .put("subject", "Application for Java Backend Developer")
                .put("body", "John Doe\\nJava Backend Developer\\n2 years experience\\nJava\\nSpring Boot\\nREST APIs\\nPostgreSQL")
                .put("attachmentName", "john_doe_resume.pdf")
                .put("attachmentType", "application/pdf");

        WorkflowExecutor.ExecutionRunResult result = executor.run(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), nodes, edges, gmailPayload);

        assertThat(result.success()).isTrue();
        assertThat(aiInput.get()).isNotNull();
        assertThat(aiInput.get().path("body").asText())
                .contains("John Doe", "Spring Boot", "PostgreSQL")
                .doesNotContain("resume apply for java developer");
        assertThat(aiInput.get().path("jobDescription").asText())
                .isEqualTo("We are hiring a Java Backend Developer. Required skills: Java, Spring Boot, REST APIs, PostgreSQL. Git and Docker are preferred.");
        assertThat(aiInput.get().path("sender").asText()).isEqualTo("candidate@example.com");
        assertThat(aiInput.get().path("subject").asText()).contains("Application");
        assertThat(aiInput.get().path("attachmentName").asText()).isEqualTo("john_doe_resume.pdf");
    }

    private ObjectNode node(String id, String type, ObjectNode data) {
        ObjectNode node = JsonUtils.mapper().createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.set("data", data);
        return node;
    }

    private ObjectNode edge(String source, String target) {
        ObjectNode edge = JsonUtils.mapper().createObjectNode();
        edge.put("id", source + "-" + target);
        edge.put("source", source);
        edge.put("target", target);
        return edge;
    }
}
