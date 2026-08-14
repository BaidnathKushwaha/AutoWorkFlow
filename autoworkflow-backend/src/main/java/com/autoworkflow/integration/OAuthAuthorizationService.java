package com.autoworkflow.integration;

import com.autoworkflow.common.exception.IntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Builds provider authorization URLs and exchanges auth codes for tokens.
 */
@Service
@RequiredArgsConstructor
public class OAuthAuthorizationService {

    private final OAuthProviderConfig oAuthProviderConfig;

    private static final Map<String, String> AUTHORIZE_ENDPOINTS = Map.of(
            "github", "https://github.com/login/oauth/authorize",
            "slack", "https://slack.com/oauth/v2/authorize",
            "google", "https://accounts.google.com/o/oauth2/v2/auth",
            "gmail", "https://accounts.google.com/o/oauth2/v2/auth",
            "google_sheets", "https://accounts.google.com/o/oauth2/v2/auth",
            "notion", "https://api.notion.com/v1/oauth/authorize",
            "discord", "https://discord.com/api/oauth2/authorize"
    );

    private static final Map<String, String> DEFAULT_SCOPES = Map.of(
            "github", "repo",
            "slack", "chat:write,channels:read",
            "google", "https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/spreadsheets",
            "gmail", "https://www.googleapis.com/auth/gmail.modify",
            "google_sheets", "https://www.googleapis.com/auth/spreadsheets",
            "notion", "",
            "discord", "identify webhook.incoming"
    );

    public String buildAuthorizationUrl(String provider, String state) {
        OAuthProviderConfig.ProviderCreds creds = credsFor(provider);
        String endpoint = AUTHORIZE_ENDPOINTS.get(provider);
        if (endpoint == null) {
            throw new IntegrationException("Unsupported OAuth provider: " + provider);
        }

        return UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("client_id", creds.getClientId())
                .queryParam("redirect_uri", creds.getRedirectUri())
                .queryParam("scope", DEFAULT_SCOPES.getOrDefault(provider, ""))
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    private OAuthProviderConfig.ProviderCreds credsFor(String provider) {
        return switch (provider) {
            case "github" -> oAuthProviderConfig.getGithub();
            case "slack" -> oAuthProviderConfig.getSlack();
            case "google", "gmail", "google_sheets" -> oAuthProviderConfig.getGoogle();
            case "notion" -> oAuthProviderConfig.getNotion();
            case "discord" -> oAuthProviderConfig.getDiscord();
            default -> throw new IntegrationException("Unsupported OAuth provider: " + provider);
        };
    }
}

