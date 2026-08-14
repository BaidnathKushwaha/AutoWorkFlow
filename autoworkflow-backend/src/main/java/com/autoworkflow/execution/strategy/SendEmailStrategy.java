package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/**
 * "Send Email" action node - distinct from the Gmail integration node in that
 * it's meant as a simple one-shot notification step (used by templates like
 * Slack Incident Alert's escalation path) rather than full inbox management.
 * Reuses the connected Gmail token for delivery.
 */
@Component
@RequiredArgsConstructor
public class SendEmailStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "send_email"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "gmail");
        JsonNode config = ctx.getNodeConfig();
        String to = config.path("to").asText();
        String subject = config.path("subject").asText("Notification from AutoWorkflow");
        String body = config.path("body").asText(ctx.getInputPayload().toString());

        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("To: %s\r\nSubject: %s\r\n\r\n%s".formatted(to, subject, body)).getBytes());

        JsonNode response = webClientBuilder.build().post()
                .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(java.util.Map.of("raw", raw))
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("response", response);
        return NodeExecutionResult.ok(output);
    }
}
