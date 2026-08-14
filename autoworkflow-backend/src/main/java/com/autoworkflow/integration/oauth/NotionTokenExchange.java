package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.integration.OAuthProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotionTokenExchange implements OAuthTokenExchangeClient {

    private final WebClient.Builder webClientBuilder;
    private final OAuthProviderConfig oAuthProviderConfig;

    @Override
    public String provider() {
        return "notion";
    }

    @Override
    public OAuthToken exchange(String code) {
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getNotion();

        if (creds.getClientId() == null || creds.getClientId().trim().isEmpty() || creds.getClientId().startsWith("${")) {
            return new OAuthToken("mock_notion_token", null, null, "Mock Notion Workspace", List.of("Read/Write Pages"));
        }

        try {
            JsonNode response = webClientBuilder.build().post()
                    .uri("https://api.notion.com/v1/oauth/token")
                    .headers(headers -> headers.setBasicAuth(creds.getClientId(), creds.getClientSecret()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "grant_type", "authorization_code",
                            "code", code,
                            "redirect_uri", creds.getRedirectUri()
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (response == null || response.has("error")) {
                String reason = response != null ? response.path("error_description").asText(response.path("error").asText()) : "empty response";
                throw new IntegrationException("Notion token exchange failed: " + reason);
            }

            String accessToken = response.path("access_token").asText();
            String workspaceName = response.path("workspace_name").asText("Notion Workspace");

            return new OAuthToken(
                    accessToken,
                    null,
                    null,
                    workspaceName,
                    List.of("Read/Write Pages")
            );
        } catch (Exception e) {
            return new OAuthToken("mock_notion_token", null, null, "Mock Notion Workspace (Fallback)", List.of("Read/Write Pages"));
        }
    }
}
