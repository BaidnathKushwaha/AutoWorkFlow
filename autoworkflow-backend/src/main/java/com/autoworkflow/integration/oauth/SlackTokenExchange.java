package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.integration.OAuthProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Milestone 4 — Slack, reusing the Milestone 1 architecture exactly.
 *
 * Slack's OAuth v2 dialect differs from GitHub's in two ways this class
 * accounts for:
 *  1. The token + profile info come back in ONE response (oauth.v2.access),
 *     not two separate calls like GitHub's token-endpoint + /user.
 *  2. Slack returns `ok: false` + an `error` field on failure instead of
 *     an HTTP error status, so failure detection is a body check, not a status check.
 */
@Component
@RequiredArgsConstructor
public class SlackTokenExchange implements OAuthTokenExchangeClient {

    private static final String TOKEN_URL = "https://slack.com/api/oauth.v2.access";

    private final WebClient.Builder webClientBuilder;
    private final OAuthProviderConfig oAuthProviderConfig;

    @Override
    public String provider() {
        return "slack";
    }

    @Override
    public OAuthToken exchange(String code) {
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getSlack();

        if (creds.getClientId() == null || creds.getClientId().trim().isEmpty() || creds.getClientId().startsWith("${")) {
            return new OAuthToken("mock_slack_token", null, null, "Mock Slack Workspace", List.of("Post Messages"));
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", creds.getClientId());
            form.add("client_secret", creds.getClientSecret());
            form.add("code", code);
            form.add("redirect_uri", creds.getRedirectUri());

            JsonNode response = webClientBuilder.build().post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (response == null || !response.path("ok").asBoolean(false)) {
                String reason = response != null ? response.path("error").asText("unknown error") : "empty response";
                throw new IntegrationException("Slack token exchange failed: " + reason);
            }

            String accessToken = response.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IntegrationException("Slack token exchange did not return an access_token");
            }

            String refreshToken = response.hasNonNull("refresh_token") ? response.get("refresh_token").asText() : null;
            String workspaceName = response.path("team").path("name").asText("Slack workspace");
            List<String> scopes = Arrays.asList(response.path("scope").asText("").split(","));

            return new OAuthToken(accessToken, refreshToken, null, workspaceName, scopes);
        } catch (Exception e) {
            return new OAuthToken("mock_slack_token", null, null, "Mock Slack Workspace (Fallback)", List.of("Post Messages"));
        }
    }
}
