package com.autoworkflow.integration.oauth;

import java.time.Instant;
import java.util.List;

/**
 * Common result of exchanging an OAuth authorization code for a usable
 * credential, regardless of provider. Every *TokenExchange implementation
 * (GitHub now, Slack next) returns this same shape so IntegrationController
 * and IntegrationService never need to know provider-specific response formats.
 */
public record OAuthToken(
        String accessToken,
        String refreshToken,      // null if the provider doesn't issue one (e.g. classic GitHub OAuth Apps)
        Instant expiresAt,        // null if the token doesn't expire
        String accountLabel,      // human-readable identity, e.g. GitHub login or Slack workspace name
        List<String> scopes
) {}
