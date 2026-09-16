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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailPollingScheduler {
    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final Set<String> RESUME_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> RESUME_NAME_TERMS = Set.of("resume", "cv", "curriculum", "candidate", "profile");
    private static final Set<String> APPLICATION_TERMS = Set.of(
            "application", "applying", "candidate", "resume", "cv", "internship", "job", "position",
            "opportunity", "hiring", "recruitment", "recruiting", "apply", "interested in", "resume attached",
            "cv attached", "role", "vacancy"
    );
    private final WorkflowRepository workflowRepository;
    private final GmailTriggerStateRepository stateRepository;
    private final IntegrationService integrationService;
    private final IntegrationApiExecutor apiExecutor;
    private final ExecutionService executionService;
    private final WebClient.Builder webClientBuilder;
    private final ResumeAttachmentTextExtractor attachmentTextExtractor;

    @Scheduled(fixedDelay = 30000)
    @Async("workflowExecutorPool")
    public void poll() {
        for (Workflow workflow : workflowRepository.findByStatusAndDeployedTrue(com.autoworkflow.common.enums.WorkflowStatus.ACTIVE)) {
            JsonNode trigger = findEmailTrigger(workflow.getCanvasNodes());
            if (trigger == null) continue;
            try { pollWorkflow(workflow, trigger.path("data")); }
            catch (Exception e) { log.warn("Gmail trigger poll failed for workflow {}: {}", workflow.getId(), safeMessage(e)); }
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
                .uri(historyUri(historyCursor))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());

        Set<String> messageIds = new LinkedHashSet<>();
        historyResponse.path("history").forEach(h -> h.path("messagesAdded").forEach(added -> {
            String id = added.path("message").path("id").asText();
            if (!id.isBlank()) messageIds.add(id);
        }));

        String nextHistoryId = historyResponse.path("historyId").asText(historyCursor);
        for (String messageId : messageIds) {
            if (messageId.isBlank()) throw new IllegalArgumentException("No message ID available.");
            ObjectNode normalized = normalize(fetchMessage(token, messageId));
            boolean application = matchesFilters(normalized, config) && isApplicationEmail(normalized);
            normalized.put("isApplication", application);
            if (application) {
                enrichResumeAttachment(token, messageId, normalized);
                executionService.execute(workflow.getId(), TriggeredBy.EMAIL_RECEIVED, normalized);
            }
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
        if (id == null || id.isBlank()) throw new IllegalArgumentException("No message ID available.");
        return apiExecutor.execute("gmail", "get trigger message", () -> webClientBuilder.build().get()
                .uri(messageUri(id))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30)).block());
    }

    private void enrichResumeAttachment(String token, String messageId, ObjectNode normalized) {
        JsonNode selected = selectResumeAttachment(normalized.path("attachments"));
        if (selected == null) throw new IllegalArgumentException("No supported resume attachment found in Gmail message.");

        String filename = selected.path("filename").asText("");
        String mimeType = selected.path("mimeType").asText("");
        String attachmentId = selected.path("attachmentId").asText("");
        if (filename.isBlank()) throw new IllegalArgumentException("No supported resume attachment found in Gmail message.");

        final String attachmentName = filename;
        final String attachmentMime = mimeType;
        final byte[] bytes;
        if (!attachmentId.isBlank()) {
            bytes = downloadAttachment(token, messageId, attachmentId, attachmentName);
        } else {
            throw new IllegalArgumentException("Gmail attachment download failed: no attachment ID for " + attachmentName + ".");
        }

        if (bytes.length == 0) throw new IllegalArgumentException("Resume attachment is empty: " + attachmentName);

        try {
            String text = attachmentTextExtractor.extract(attachmentName, attachmentMime, bytes);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Resume text extraction failed: no readable text found in " + attachmentName);
            }
            normalized.put("body", text);
            normalized.put("attachmentName", attachmentName);
            normalized.put("attachmentType", attachmentMime);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Resume text extraction failed for " + attachmentName + ".", e);
        }
    }

    private byte[] downloadAttachment(String token, String messageId, String attachmentId, String filename) {
        try {
            JsonNode response = apiExecutor.execute("gmail", "get resume attachment", () -> webClientBuilder.build().get()
                    .uri(attachmentUri(messageId, attachmentId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30)).block());
            String data = response.path("data").asText("");
            if (data.isBlank()) throw new IllegalArgumentException("Gmail attachment download failed: empty attachment data.");
            return decodeBytes(data);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Gmail attachment download failed for " + filename + ".", e);
        }
    }

    private URI historyUri(String historyCursor) {
        return UriComponentsBuilder.fromUriString(BASE + "/history")
                .queryParam("startHistoryId", historyCursor)
                .queryParam("historyTypes", "messageAdded")
                .build()
                .toUri();
    }

    private URI messageUri(String messageId) {
        return URI.create(BASE + "/messages/" + UriUtils.encodePathSegment(messageId, StandardCharsets.UTF_8) + "?format=full");
    }

    private URI attachmentUri(String messageId, String attachmentId) {
        return URI.create(BASE + "/messages/"
                + UriUtils.encodePathSegment(messageId, StandardCharsets.UTF_8)
                + "/attachments/"
                + UriUtils.encodePathSegment(attachmentId, StandardCharsets.UTF_8));
    }

    JsonNode selectResumeAttachment(JsonNode attachments) {
        if (attachments == null || !attachments.isArray()) return null;
        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode attachment : attachments) {
            String filename = attachment.path("filename").asText("");
            if (!RESUME_EXTENSIONS.contains(extension(filename))) continue;
            String lower = filename.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String term : RESUME_NAME_TERMS) if (lower.contains(term)) score += 100;
            if (best == null || score > bestScore) {
                best = attachment;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isApplicationEmail(JsonNode message) {
        String subject = message.path("subject").asText("").toLowerCase(Locale.ROOT);
        String body = message.path("body").asText("").toLowerCase(Locale.ROOT);
        int textSignals = 0;
        for (String term : APPLICATION_TERMS) {
            if (subject.contains(term)) textSignals += 2;
            else if (body.contains(term)) textSignals++;
        }
        boolean hasResumeAttachment = false;
        JsonNode attachments = message.path("attachments");
        if (attachments.isArray()) hasResumeAttachment = selectResumeAttachment(attachments) != null;
        return textSignals >= 2 || (textSignals >= 1 && hasResumeAttachment);
    }

    private ObjectNode normalize(JsonNode message) {
        if (message == null || !message.isObject()) throw new IllegalArgumentException("Gmail message payload is missing.");
        String messageId = message.path("id").asText("");
        if (messageId.isBlank()) throw new IllegalArgumentException("No message ID available.");
        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("messageId", messageId);
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
        output.put("isApplication", false);
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

    private byte[] decodeBytes(String data) {
        try { return Base64.getUrlDecoder().decode(data); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Gmail attachment data is invalid."); }
    }

    private String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]");
    }

    private JsonNode findEmailTrigger(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) return null;
        for (JsonNode node : nodes) if ("email_received".equals(node.path("type").asText())) return node;
        return null;
    }
}
