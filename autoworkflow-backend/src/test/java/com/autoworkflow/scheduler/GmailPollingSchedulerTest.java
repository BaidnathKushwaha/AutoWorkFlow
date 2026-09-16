package com.autoworkflow.scheduler;

import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.common.enums.WorkflowStatus;
import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GmailPollingSchedulerTest {

    @Test
    void newApplicationEmailDownloadsRealAttachmentExtractsBodyAndTriggersEmailExecution() throws Exception {
        UUID workflowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Workflow workflow = Workflow.builder()
                .id(workflowId)
                .userId(userId)
                .name("Resume Matcher")
                .status(WorkflowStatus.ACTIVE)
                .deployed(true)
                .canvasNodes(JsonUtils.mapper().readTree("""
                        [{"type":"email_received","data":{"subjectFilter":"Application","jobDescription":"We are hiring a Java Backend Developer. Required skills: Java, Spring Boot, REST APIs, PostgreSQL."}}]
                        """))
                .build();

        GmailTriggerState state = GmailTriggerState.builder()
                .workflowId(workflowId)
                .historyId("100")
                .updatedAt(Instant.now())
                .build();

        byte[] resumePdf = createPdf("John Doe\nJava Backend Developer\n2 years experience\nJava\nSpring Boot\nREST APIs\nPostgreSQL");
        String encodedResume = Base64.getUrlEncoder().withoutPadding().encodeToString(resumePdf);
        String encodedEmailBody = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "Hello, I am applying for the Java Backend Developer position. Please find my resume attached."
                        .getBytes(StandardCharsets.UTF_8));

        String historyJson = """
                {
                  "historyId":"200",
                  "history":[
                    {"id":"history-1","messagesAdded":[{"message":{"id":"message-1","threadId":"thread-1"}}]}
                  ]
                }
                """;
        String messageJson = """
                {
                  "id":"message-1",
                  "threadId":"thread-1",
                  "internalDate":"1778947200000",
                  "labelIds":["INBOX"],
                  "payload":{
                    "mimeType":"multipart/mixed",
                    "headers":[
                      {"name":"From","value":"candidate@example.com"},
                      {"name":"To","value":"jobs@example.com"},
                      {"name":"Subject","value":"Application for Java Backend Developer"}
                    ],
                    "parts":[
                      {"mimeType":"text/plain","body":{"data":"%s"}},
                      {"filename":"john_doe_resume.pdf","mimeType":"application/pdf","body":{"attachmentId":"attachment-1","size":%d}}
                    ]
                  }
                }
                """.formatted(encodedEmailBody, resumePdf.length);
        String attachmentJson = "{\"size\":%d,\"data\":\"%s\"}".formatted(resumePdf.length, encodedResume);

        AtomicReference<String> attachmentRequest = new AtomicReference<>();
        ExchangeFunction exchange = request -> responseFor(request, historyJson, messageJson, attachmentJson, attachmentRequest);
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchange);

        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        GmailTriggerStateRepository stateRepository = mock(GmailTriggerStateRepository.class);
        IntegrationService integrationService = mock(IntegrationService.class);
        IntegrationApiExecutor apiExecutor = mock(IntegrationApiExecutor.class);
        ExecutionService executionService = mock(ExecutionService.class);

        when(workflowRepository.findByStatusAndDeployedTrue(WorkflowStatus.ACTIVE)).thenReturn(List.of(workflow));
        when(stateRepository.findByWorkflowId(workflowId)).thenReturn(Optional.of(state));
        when(integrationService.getDecryptedAccessToken(userId, "gmail")).thenReturn("test-gmail-access-token");
        when(apiExecutor.<JsonNode>execute(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<JsonNode>) invocation.getArgument(2)).get());
        when(executionService.execute(eq(workflowId), eq(TriggeredBy.EMAIL_RECEIVED), any(JsonNode.class))).thenReturn(null);

        GmailPollingScheduler scheduler = new GmailPollingScheduler(
                workflowRepository,
                stateRepository,
                integrationService,
                apiExecutor,
                executionService,
                webClientBuilder,
                new ResumeAttachmentTextExtractor());

        scheduler.poll();

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(executionService).execute(eq(workflowId), eq(TriggeredBy.EMAIL_RECEIVED), payloadCaptor.capture());

        JsonNode payload = payloadCaptor.getValue();
        assertThat(payload.path("sender").asText()).isEqualTo("candidate@example.com");
        assertThat(payload.path("subject").asText()).isEqualTo("Application for Java Backend Developer");
        assertThat(payload.path("attachmentName").asText()).isEqualTo("john_doe_resume.pdf");
        assertThat(payload.path("attachmentType").asText()).isEqualTo("application/pdf");
        assertThat(payload.path("body").asText())
                .contains("John Doe")
                .contains("Java Backend Developer")
                .contains("Spring Boot")
                .contains("PostgreSQL");
        assertThat(payload.path("body").asText()).doesNotContain("Please find my resume attached");
        assertThat(attachmentRequest.get()).isEqualTo("/gmail/v1/users/me/messages/message-1/attachments/attachment-1");
        assertThat(state.getHistoryId()).isEqualTo("200");
        verify(stateRepository).save(state);
    }

    @Test
    void firstPollInitializesCursorAndDoesNotProcessExistingEmail() throws Exception {
        UUID workflowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Workflow workflow = Workflow.builder()
                .id(workflowId)
                .userId(userId)
                .name("Resume Matcher")
                .status(WorkflowStatus.ACTIVE)
                .deployed(true)
                .canvasNodes(JsonUtils.mapper().readTree("[{\"type\":\"email_received\",\"data\":{}}]"))
                .build();

        String profileJson = "{\"historyId\":\"999\"}";
        AtomicReference<String> requestedPath = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            requestedPath.set(request.url().getPath());
            return Mono.just(jsonResponse(profileJson));
        };

        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        GmailTriggerStateRepository stateRepository = mock(GmailTriggerStateRepository.class);
        IntegrationService integrationService = mock(IntegrationService.class);
        IntegrationApiExecutor apiExecutor = mock(IntegrationApiExecutor.class);
        ExecutionService executionService = mock(ExecutionService.class);

        when(workflowRepository.findByStatusAndDeployedTrue(WorkflowStatus.ACTIVE)).thenReturn(List.of(workflow));
        when(stateRepository.findByWorkflowId(workflowId)).thenReturn(Optional.empty());
        when(integrationService.getDecryptedAccessToken(userId, "gmail")).thenReturn("test-gmail-access-token");
        when(apiExecutor.<JsonNode>execute(anyString(), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<JsonNode>) invocation.getArgument(2)).get());

        GmailPollingScheduler scheduler = new GmailPollingScheduler(
                workflowRepository,
                stateRepository,
                integrationService,
                apiExecutor,
                executionService,
                WebClient.builder().exchangeFunction(exchange),
                new ResumeAttachmentTextExtractor());

        scheduler.poll();

        ArgumentCaptor<GmailTriggerState> stateCaptor = ArgumentCaptor.forClass(GmailTriggerState.class);
        verify(stateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getHistoryId()).isEqualTo("999");
        verifyNoInteractions(executionService);
        assertThat(requestedPath.get()).isEqualTo("/gmail/v1/users/me/profile");
    }

    @Test
    void inactiveOrUndeployedWorkflowIsNotPolled() {
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        when(workflowRepository.findByStatusAndDeployedTrue(WorkflowStatus.ACTIVE)).thenReturn(List.of());

        GmailPollingScheduler scheduler = new GmailPollingScheduler(
                workflowRepository,
                mock(GmailTriggerStateRepository.class),
                mock(IntegrationService.class),
                mock(IntegrationApiExecutor.class),
                mock(ExecutionService.class),
                mock(WebClient.Builder.class),
                new ResumeAttachmentTextExtractor());

        scheduler.poll();

        verify(workflowRepository).findByStatusAndDeployedTrue(WorkflowStatus.ACTIVE);
    }

    private static Mono<ClientResponse> responseFor(
            ClientRequest request,
            String historyJson,
            String messageJson,
            String attachmentJson,
            AtomicReference<String> attachmentRequest) {
        String path = request.url().getPath();
        if (path.endsWith("/history")) return Mono.just(jsonResponse(historyJson));
        if (path.endsWith("/messages/message-1")) return Mono.just(jsonResponse(messageJson));
        if (path.endsWith("/messages/message-1/attachments/attachment-1")) {
            attachmentRequest.set(path);
            return Mono.just(jsonResponse(attachmentJson));
        }
        return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
    }

    private static ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                for (String line : text.split("\\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
