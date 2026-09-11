package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gmail send/read/search integration. OAuth tokens are used only for the outbound request. */
@Component
@RequiredArgsConstructor
public class GmailIntegrationStrategy implements NodeStrategy {

    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}" );

    private final WebClient.Builder webClientBuilder;
    private final IntegrationService integrationService;
    private final IntegrationApiExecutor apiExecutor;

    @Override public String getTypeKey() { return "gmail"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        String token = integrationService.getDecryptedAccessToken(ctx.getUserId(), "gmail");
        JsonNode config = ctx.getNodeConfig();
        String action = config.path("action").asText("send").toLowerCase();

        return switch (action) {
            case "send" -> send(ctx, token);
            case "read", "search" -> read(ctx, token, action);
            default -> throw new IllegalArgumentException("Unsupported Gmail action: " + action);
        };
    }

    private NodeExecutionResult send(NodeExecutionContext ctx, String token) {
        JsonNode config = ctx.getNodeConfig();
        String to = substitute(config.path("to").asText(), ctx.getInputPayload());
        String subject = substitute(config.path("subject").asText(), ctx.getInputPayload());
        String body = substitute(config.path("body").asText("{{input}}"), ctx.getInputPayload());
        if (to.isBlank()) throw new IllegalArgumentException("Gmail recipient is required for send.");
        if (subject.isBlank()) throw new IllegalArgumentException("Gmail subject is required for send.");

        String rawMessage = "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n" + body;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));

        JsonNode response = apiExecutor.execute("gmail", "send message", () ->
                webClientBuilder.build().post()
                        .uri(BASE + "/messages/send")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .bodyValue(java.util.Map.of("raw", encoded))
                        .retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.put("messageId", response.path("id").asText());
        output.put("threadId", response.path("threadId").asText());
        return NodeExecutionResult.ok(output);
    }

    private NodeExecutionResult read(NodeExecutionContext ctx, String token, String action) {
        JsonNode config = ctx.getNodeConfig();
        int maxResults = Math.max(1, Math.min(config.path("maxResults").asInt(10), 100));
        String query = action.equals("search") ? config.path("query").asText("") : "";

        JsonNode list = apiExecutor.execute("gmail", "list messages", () ->
                webClientBuilder.build().get().uri(uriBuilder -> uriBuilder
                                .path(BASE + "/messages")
                                .queryParam("maxResults", maxResults)
                                .queryParamIfPresent("q", query.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(query))
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve().bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30)).block());

        ArrayNode messages = JsonUtils.mapper().createArrayNode();
        list.path("messages").forEach(summary -> {
            String id = summary.path("id").asText();
            if (id.isBlank()) return;
            JsonNode message = apiExecutor.execute("gmail", "get message", () ->
                    webClientBuilder.build().get().uri(BASE + "/messages/" + id + "?format=full")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve().bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(30)).block());
            messages.add(normalize(message));
        });

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("messages", messages);
        output.put("count", messages.size());
        output.put("action", action);
        return NodeExecutionResult.ok(output);
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
        if (body.isBlank()) body = message.path("snippet").asText("");
        output.put("body", body);
        return output;
    }

    private String header(JsonNode headers, String name) {
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(header.path("name").asText())) return header.path("value").asText("");
        }
        return "";
    }

    private String extractText(JsonNode part) {
        String mime = part.path("mimeType").asText("");
        String data = part.path("body").path("data").asText("");
        if (!data.isBlank() && (mime.startsWith("text/plain") || mime.isBlank())) return decode(data);
        if (part.path("parts").isArray()) {
            for (JsonNode child : part.path("parts")) {
                String childMime = child.path("mimeType").asText("");
                if (childMime.startsWith("text/plain")) {
                    String text = extractText(child);
                    if (!text.isBlank()) return text;
                }
            }
            for (JsonNode child : part.path("parts")) {
                String text = extractText(child);
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private String decode(String data) {
        try {
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String substitute(String template, JsonNode input) {
        Matcher matcher = TEMPLATE.matcher(template == null ? "" : template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement;
            if ("input".equals(key)) replacement = input == null ? "" : (input.isTextual() ? input.asText() : input.toString());
            else {
                JsonNode value = input == null ? null : input.at(key.startsWith("/") ? key : "/" + key.replace('.', '/'));
                replacement = value == null || value.isMissingNode() ? "" : (value.isTextual() ? value.asText() : value.toString());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
