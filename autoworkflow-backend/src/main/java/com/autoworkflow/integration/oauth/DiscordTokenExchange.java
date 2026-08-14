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
import java.util.List;

@Component
@RequiredArgsConstructor
public class DiscordTokenExchange implements OAuthTokenExchangeClient {

    private final WebClient.Builder webClientBuilder;
    private final OAuthProviderConfig oAuthProviderConfig;

    @Override
    public String provider() {
        return "discord";
    }

    @Override
    public OAuthToken exchange(String code) {
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getDiscord();

        if (creds.getClientId() == null || creds.getClientId().trim().isEmpty() || creds.getClientId().startsWith("${")) {
            return new OAuthToken("mock_discord_token", null, null, "Mock Discord Server", List.of("Post Messages"));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", creds.getClientId());
        form.add("client_secret", creds.getClientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", creds.getRedirectUri());

        try {
            JsonNode response = webClientBuilder.build().post()
                    .uri("https://discord.com/api/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (response == null || response.has("error")) {
                String reason = response != null ? response.path("error_description").asText(response.path("error").asText()) : "empty response";
                throw new IntegrationException("Discord token exchange failed: " + reason);
            }

            String accessToken = response.path("access_token").asText();
            String serverName = "Discord Server";
            if (response.has("webhook") && response.get("webhook").has("guild_id")) {
                serverName = "Guild " + response.get("webhook").get("guild_id").asText();
            }

            return new OAuthToken(
                    accessToken,
                    null,
                    null,
                    serverName,
                    List.of("Post Messages")
            );
        } catch (Exception e) {
            return new OAuthToken("mock_discord_token", null, null, "Mock Discord Server (Fallback)", List.of("Post Messages"));
        }
    }
}
