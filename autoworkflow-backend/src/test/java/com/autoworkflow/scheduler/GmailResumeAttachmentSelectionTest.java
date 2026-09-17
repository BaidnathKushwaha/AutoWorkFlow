package com.autoworkflow.scheduler;

import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.autoworkflow.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class GmailResumeAttachmentSelectionTest {

    @Test
    void prefersResumeNamedDocumentAndIgnoresUnsupportedFiles() throws Exception {
        GmailPollingScheduler scheduler = scheduler();
        JsonNode attachments = JsonUtils.mapper().readTree("""
                [
                  {"filename":"cover-letter.pdf","mimeType":"application/pdf","attachmentId":"1"},
                  {"filename":"profile.png","mimeType":"image/png","attachmentId":"2"},
                  {"filename":"john_resume.docx","mimeType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document","attachmentId":"3"}
                ]
                """);

        JsonNode selected = scheduler.selectResumeAttachment(attachments);

        assertNotNull(selected);
        assertEquals("john_resume.docx", selected.path("filename").asText());
    }

    @Test
    void fallsBackToFirstSupportedDocumentWhenNoResumeNameExists() throws Exception {
        GmailPollingScheduler scheduler = scheduler();
        JsonNode attachments = JsonUtils.mapper().readTree("""
                [
                  {"filename":"cover-letter.pdf","mimeType":"application/pdf","attachmentId":"1"},
                  {"filename":"profile.png","mimeType":"image/png","attachmentId":"2"},
                  {"filename":"portfolio.docx","mimeType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document","attachmentId":"3"}
                ]
                """);

        JsonNode selected = scheduler.selectResumeAttachment(attachments);

        assertNotNull(selected);
        assertEquals("cover-letter.pdf", selected.path("filename").asText());
    }

    @Test
    void returnsNullWhenNoSupportedDocumentExists() throws Exception {
        GmailPollingScheduler scheduler = scheduler();
        JsonNode attachments = JsonUtils.mapper().readTree("""
                [
                  {"filename":"profile.png","mimeType":"image/png","attachmentId":"1"},
                  {"filename":"archive.zip","mimeType":"application/zip","attachmentId":"2"}
                ]
                """);

        assertNull(scheduler.selectResumeAttachment(attachments));
    }

    private GmailPollingScheduler scheduler() {
        return new GmailPollingScheduler(
                Mockito.mock(WorkflowRepository.class),
                Mockito.mock(GmailTriggerStateRepository.class),
                Mockito.mock(IntegrationService.class),
                Mockito.mock(IntegrationApiExecutor.class),
                Mockito.mock(ExecutionService.class),
                Mockito.mock(WebClient.Builder.class),
                new ResumeAttachmentTextExtractor());
    }
}
