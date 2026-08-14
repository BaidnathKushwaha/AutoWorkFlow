package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/** Sends an SMS via Twilio using a platform-level Twilio account (not a per-user OAuth integration). */
@Component
@RequiredArgsConstructor
public class SmsStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${app.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${app.twilio.from-number:}")
    private String twilioFromNumber;

    @Override public String getTypeKey() { return "sms"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        if (twilioAccountSid.isBlank()) {
            return NodeExecutionResult.failed("Twilio is not configured. Set app.twilio.account-sid / auth-token / from-number.");
        }
        JsonNode config = ctx.getNodeConfig();
        String to = config.path("to").asText();
        String body = config.path("message").asText(ctx.getInputPayload().toString());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", twilioFromNumber);
        form.add("Body", body);

        String basicAuth = Base64.getEncoder().encodeToString((twilioAccountSid + ":" + twilioAuthToken).getBytes());

        JsonNode response = webClientBuilder.build().post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("response", response);
        return NodeExecutionResult.ok(output);
    }
}
