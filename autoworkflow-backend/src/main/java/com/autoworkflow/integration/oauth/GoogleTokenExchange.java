package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.integration.OAuthProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
        if (code == null || code.isBlank()) {
            throw new IntegrationException("Google authorization code is missing.");
        }
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getGoogle();
        requireConfigured(creds);

        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("client_id", creds.getClientId());
        form.add("client_secret", creds.getClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", creds.getRedirectUri());
        return requestToken(form, "authorization");
    }

    public OAuthToken refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IntegrationException("Google refresh token is unavailable. Reconnect the integration.");
        }
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getGoogle();
        requireConfigured(creds);

        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("client_id", creds.getClientId());
        form.add("client_secret", creds.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return requestToken(form, "refresh");
    }

    private OAuthToken requestToken(org.springframework.util.MultiValueMap<String, String> form, String operation) {
        try {
            JsonNode response = webClientBuilder.build().post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (response == null || response.has("error") || response.path("access_token").asText().isBlank()) {
                throw new IntegrationException("Google token " + operation + " failed. Please reconnect the integration.");
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
        } catch (WebClientResponseException e) {
            throw new IntegrationException("Google token " + operation + " failed. Please reconnect the integration.", e);
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("Google token " + operation + " failed. Check Google OAuth configuration and try again.", e);
        }
    }

    private void requireConfigured(OAuthProviderConfig.ProviderCreds creds) {
        if (creds.getClientId() == null || creds.getClientId().isBlank() || creds.getClientId().startsWith("${") ||
                creds.getClientSecret() == null || creds.getClientSecret().isBlank() || creds.getClientSecret().startsWith("${") ||
                creds.getRedirectUri() == null || creds.getRedirectUri().isBlank() || creds.getRedirectUri().startsWith("${")) {
            throw new IntegrationException("Google OAuth is not configured. Set the Google client ID, client secret, and redirect URI.");
        }
    }
}
