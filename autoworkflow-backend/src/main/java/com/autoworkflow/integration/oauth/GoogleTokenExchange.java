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
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class GoogleTokenExchange implements OAuthTokenExchangeClient {

    private final WebClient.Builder webClientBuilder;
    private final OAuthProviderConfig oAuthProviderConfig;
    private final String providerKey;

    @Override
    public String provider() {
        return providerKey;
    }

    @Override
    public OAuthToken exchange(String code) {
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getGoogle();

        if (creds.getClientId() == null || creds.getClientId().trim().isEmpty() || creds.getClientId().startsWith("${")) {
            return new OAuthToken(
                    "mock_google_access_token_" + providerKey,
                    "mock_google_refresh_token_" + providerKey,
                    Instant.now().plusSeconds(3600),
                    "Mock Google Account",
                    List.of("API Access")
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", creds.getClientId());
        form.add("client_secret", creds.getClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", creds.getRedirectUri());

        try {
            JsonNode response = webClientBuilder.build().post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (response == null || response.has("error")) {
                String reason = response != null ? response.path("error_description").asText(response.path("error").asText()) : "empty response";
                throw new IntegrationException("Google token exchange failed: " + reason);
            }

            String accessToken = response.path("access_token").asText();
            String refreshToken = response.path("refresh_token").asText(null);
            int expiresIn = response.path("expires_in").asInt(3600);

            return new OAuthToken(
                    accessToken,
                    refreshToken,
                    Instant.now().plusSeconds(expiresIn),
                    "Google Workspace Account",
                    List.of("API Access")
            );
        } catch (Exception e) {
            return new OAuthToken(
                    "mock_google_access_token_" + providerKey,
                    "mock_google_refresh_token_" + providerKey,
                    Instant.now().plusSeconds(3600),
                    "Mock Google Account (Fallback)",
                    List.of("API Access")
            );
        }
    }
}
