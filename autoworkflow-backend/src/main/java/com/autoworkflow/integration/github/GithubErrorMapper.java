package com.autoworkflow.integration.github;

import com.autoworkflow.integration.http.IntegrationErrorMapper;
import org.springframework.stereotype.Component;

@Component
public class GithubErrorMapper implements IntegrationErrorMapper {

    @Override public String getProviderKey() { return "github"; }

    @Override
    public String mapHttpError(int status, String rawProviderMessage) {
        return switch (status) {
            case 401 -> "Reconnect GitHub — your connection has expired or been revoked.";
            case 403 -> "GitHub denied this request. It may be rate-limited, or the token lacks access to this repository.";
            case 404 -> "Repository not found. Check the owner/repo name and that your GitHub connection has access to it.";
            case 422 -> "GitHub rejected the request: " + shortOrDefault(rawProviderMessage, "the input didn't pass validation.");
            case 429 -> "GitHub rate limit hit. This will retry automatically.";
            default -> null; // fall back to a generic message
        };
    }

    private String shortOrDefault(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }
}
