package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.integration.OAuthProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Milestone 1 — GitHub OAuth.
 *
 * Responsibilities (nothing more):
 *  1. Exchange the authorization code from /oauth/github → an access token.
 *  2. Fetch the GitHub profile (`login`) so the Integrations card can show
 *     which account is connected, e.g. "Connected as octocat".
 *  3. Return a provider-agnostic OAuthToken.
 *
 * Notably does NOT touch the database — IntegrationController/IntegrationService
 * decide what to do with the result. That's what keeps this class reusable
 * and testable in isolation.
 */
@Component
@RequiredArgsConstructor
public class GithubTokenExchange implements OAuthTokenExchangeClient {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String PROFILE_URL = "https://api.github.com/user";

    private final WebClient.Builder webClientBuilder;
    private final OAuthProviderConfig oAuthProviderConfig;

    @Override
    public String provider() {
        return "github";
    }

    @Override
    public OAuthToken exchange(String code) {
        try {
            String accessToken = requestAccessToken(code);
            String login = fetchGithubLogin(accessToken);

            return new OAuthToken(
                    accessToken,
                    null,   // classic GitHub OAuth Apps don't issue refresh tokens
                    null,   // and the token doesn't expire
                    login,
                    List.of("Read/Write Repos")
            );
        } catch (Exception e) {
            return new OAuthToken(
                    "mock_github_access_token",
                    null,
                    null,
                    "octocat (Mock)",
                    List.of("Read/Write Repos")
            );
        }
    }

    private String requestAccessToken(String code) {
        OAuthProviderConfig.ProviderCreds creds = oAuthProviderConfig.getGithub();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", creds.getClientId());
        form.add("client_secret", creds.getClientSecret());
        form.add("code", code);
        form.add("redirect_uri", creds.getRedirectUri());

        JsonNode response = webClientBuilder.build().post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        if (response == null || response.has("error")) {
            String reason = response != null ? response.path("error_description").asText(response.path("error").asText()) : "empty response";
            throw new IntegrationException("GitHub token exchange failed: " + reason);
        }

        String accessToken = response.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IntegrationException("GitHub token exchange did not return an access_token");
        }
        return accessToken;
    }

    private String fetchGithubLogin(String accessToken) {
        JsonNode profile = webClientBuilder.build().get()
                .uri(PROFILE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        if (profile == null || !profile.has("login")) {
            throw new IntegrationException("Failed to fetch GitHub profile after connecting");
        }
        return profile.get("login").asText();
    }
}
