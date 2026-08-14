package com.autoworkflow.integration.slack;

import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Renamed from SlackApiClient for symmetry with GithubIssueClient /
 * GithubRepositoryClient — one client per Slack resource. Only messages
 * exist today; if Slack grows (channels, files, reactions), those get their
 * own SlackXClient rather than piling onto this one.
 */
@Component
@RequiredArgsConstructor
public class SlackMessageClient {

    private static final String POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";

    private final WebClient.Builder webClientBuilder;
    private final IntegrationApiExecutor apiExecutor;

    public SlackMessageResponse postMessage(String accessToken, String channel, String text) {
        return apiExecutor.execute("slack", "post_message", () -> {
            JsonNode response = webClientBuilder.build().post()
                    .uri(POST_MESSAGE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .bodyValue(Map.of("channel", channel, "text", text))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            // Slack returns HTTP 200 even on failure — success is the `ok` field in the body.
            if (response == null || !response.path("ok").asBoolean(false)) {
                String errorCode = response != null ? response.path("error").asText("unknown_error") : "empty_response";
                throw apiExecutor.appError("slack", "post_message", errorCode, errorCode);
            }
            return SlackMessageResponse.fromApiResponse(response);
        });
    }
}
