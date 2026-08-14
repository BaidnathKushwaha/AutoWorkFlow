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
 * Reads or sends email via the Gmail API using the user's connected Google OAuth token.
 * Sending uses the raw RFC 2822 message format required by users.messages.send.
 */
@Component
@RequiredArgsConstructor
public class GmailIntegrationStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;

    @Override public String getTypeKey() { return "gmail"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "gmail");
        JsonNode config = ctx.getNodeConfig();
        String action = config.path("action").asText("send");

        ObjectNode output = JsonUtils.mapper().createObjectNode();

        if ("send".equals(action)) {
            String to = config.path("to").asText();
            String subject = config.path("subject").asText();
            String body = config.path("body").asText(ctx.getInputPayload().toString());
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("To: %s\r\nSubject: %s\r\n\r\n%s".formatted(to, subject, body)).getBytes());

            JsonNode response = webClientBuilder.build().post()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .bodyValue(java.util.Map.of("raw", raw))
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();
            output.set("response", response);
        } else {
            JsonNode response = webClientBuilder.build().get()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();
            output.set("response", response);
        }
        return NodeExecutionResult.ok(output);
    }
}
