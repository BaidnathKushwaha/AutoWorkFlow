package com.autoworkflow.scheduler;

import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailPollingScheduler {
    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private final WorkflowRepository workflowRepository;
    private final GmailTriggerStateRepository stateRepository;
    private final IntegrationService integrationService;
    private final IntegrationApiExecutor apiExecutor;
    private final ExecutionService executionService;
    private final WebClient.Builder webClientBuilder;

    @Scheduled(fixedDelay = 30000)
    @Async("workflowExecutorPool")
    public void poll() {
        for (Workflow workflow : workflowRepository.findByStatusAndDeployedTrue(com.autoworkflow.common.enums.WorkflowStatus.ACTIVE)) {
            JsonNode trigger = findEmailTrigger(workflow.getCanvasNodes());
            if (trigger == null) continue;
            try { pollWorkflow(workflow, trigger.path("data")); }
            catch (Exception e) { log.warn("Gmail trigger poll failed for workflow {}: {}", workflow.getId(), e.getMessage()); }
        }
    }

    private void pollWorkflow(Workflow workflow, JsonNode config) {
        String token = integrationService.getDecryptedAccessToken(workflow.getUserId(), "gmail");
        GmailTriggerState state = stateRepository.findByWorkflowId(workflow.getId()).orElse(null);
        if (state == null) {
            stateRepository.save(GmailTriggerState.builder().workflowId(workflow.getId())
                    .historyId(currentHistoryId(token)).updatedAt(Instant.now()).build());
            return;
        }

        String historyCursor = state.getHistoryId();
        JsonNode historyResponse = apiExecutor.execute("gmail", "read history", () -> webClientBuilder.build().get()
                .uri(uri -> uri.path(BASE + "/history").queryParam("startHistoryId", historyCursor)
                        .queryParam("historyTypes", "messageAdded").build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());

        Set<String> messageIds = new LinkedHashSet<>();
        historyResponse.path("history").forEach(h -> h.path("messagesAdded").forEach(added -> {
            String id = added.path("message").path("id").asText();
            if (!id.isBlank()) messageIds.add(id);
        }));

        String nextHistoryId = historyResponse.path("historyId").asText(historyCursor);
        for (String messageId : messageIds) {
            ObjectNode normalized = normalize(fetchMessage(token, messageId));
            enrichTextAttachments(token, messageId, normalized);
            if (matchesFilters(normalized, config)) executionService.execute(workflow.getId(), TriggeredBy.EMAIL_RECEIVED, normalized);
        }
        state.setHistoryId(nextHistoryId);
        state.setUpdatedAt(Instant.now());
        stateRepository.save(state);
    }

    private String currentHistoryId(String token) {
        JsonNode profile = apiExecutor.execute("gmail", "read profile", () -> webClientBuilder.build().get().uri(BASE + "/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());
        String historyId = profile.path("historyId").asText();
        if (historyId.isBlank()) throw new IllegalStateException("Gmail did not return a history cursor.");
        return historyId;
    }

    private JsonNode fetchMessage(String token, String id) {
        return apiExecutor.execute("gmail", "get trigger message", () -> webClientBuilder.build().get()
                .uri(BASE + "/messages/" + id + "?format=full")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());
    }

    private void enrichTextAttachments(String token, String messageId, ObjectNode normalized) {
        StringBuilder attachmentText = new StringBuilder();
        for (JsonNode attachment : normalized.path("attachments")) {
            String mime = attachment.path("mimeType").asText("");
            String attachmentId = attachment.path("attachmentId").asText("");
            if (!mime.startsWith("text/") || attachmentId.isBlank()) continue;
            try {
                JsonNode response = apiExecutor.execute("gmail", "get attachment", () -> webClientBuilder.build().get()
                        .uri(BASE + "/messages/" + messageId + "/attachments/" + attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());
                String text = decode(response.path("data").asText(""));
                if (!text.isBlank()) {
                    ((ObjectNode) attachment).put("text", text);
                    attachmentText.append("\n\n").append(text);
                }
            } catch (Exception e) {
                log.debug("Unable to read text Gmail attachment {}: {}", attachment.path("filename").asText(), e.getMessage());
            }
        }
        if (attachmentText.length() > 0) normalized.put("body", normalized.path("body").asText("") + attachmentText);
    }

    private ObjectNode normalize(JsonNode message) {
        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("messageId", message.path("id").asText());
        output.put("threadId", message.path("threadId").asText());
        output.put("timestamp", message.path("internalDate").asText());
        output.set("labels", message.path("labelIds").isArray() ? message.path("labelIds") : JsonUtils.mapper().createArrayNode());
        JsonNode headers = message.path("payload").path("headers");
        output.put("sender", header(headers, "From"));
        output.put("recipients", header(headers, "To"));
        output.put("subject", header(headers, "Subject"));
        String body = extractText(message.path("payload"));
        output.put("body", body.isBlank() ? message.path("snippet").asText("") : body);
        output.set("attachments", attachmentMetadata(message.path("payload")));
        return output;
    }

    private boolean matchesFilters(JsonNode message, JsonNode config) {
        String from = config.path("fromFilter").asText("").trim().toLowerCase();
        String subject = config.path("subjectFilter").asText("").trim().toLowerCase();
        return (from.isBlank() || message.path("sender").asText("").toLowerCase().contains(from))
                && (subject.isBlank() || message.path("subject").asText("").toLowerCase().contains(subject));
    }

    private String header(JsonNode headers, String name) {
        for (JsonNode h : headers) if (name.equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText("");
        return "";
    }

    private String extractText(JsonNode part) {
        String mime = part.path("mimeType").asText("");
        String data = part.path("body").path("data").asText("");
        if (!data.isBlank() && (mime.startsWith("text/plain") || mime.isBlank())) return decode(data);
        if (part.path("parts").isArray()) {
            for (JsonNode child : part.path("parts")) if (child.path("mimeType").asText("").startsWith("text/plain")) {
                String text = extractText(child); if (!text.isBlank()) return text;
            }
            for (JsonNode child : part.path("parts")) { String text = extractText(child); if (!text.isBlank()) return text; }
        }
        return "";
    }

    private ArrayNode attachmentMetadata(JsonNode part) {
        ArrayNode result = JsonUtils.mapper().createArrayNode();
        collectAttachments(part, result);
        return result;
    }

    private void collectAttachments(JsonNode part, ArrayNode out) {
        if (part.path("filename").asText("").isBlank()) {
            if (part.path("parts").isArray()) part.path("parts").forEach(child -> collectAttachments(child, out));
            return;
        }
        ObjectNode attachment = JsonUtils.mapper().createObjectNode();
        attachment.put("filename", part.path("filename").asText());
        attachment.put("mimeType", part.path("mimeType").asText());
        attachment.put("attachmentId", part.path("body").path("attachmentId").asText());
        attachment.put("size", part.path("body").path("size").asInt(0));
        out.add(attachment);
        if (part.path("parts").isArray()) part.path("parts").forEach(child -> collectAttachments(child, out));
    }

    private String decode(String data) {
        try { return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return ""; }
    }

    private JsonNode findEmailTrigger(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) return null;
        for (JsonNode node : nodes) if ("email_received".equals(node.path("type").asText())) return node;
        return null;
    }
}
