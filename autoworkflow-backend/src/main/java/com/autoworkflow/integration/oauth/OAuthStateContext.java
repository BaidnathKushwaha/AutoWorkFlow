package com.autoworkflow.integration.oauth;

import java.util.UUID;

/**
 * Trusted OAuth callback context recovered from the server-side state record.
 * Neither value is taken from callback query parameters.
 */
public record OAuthStateContext(UUID userId, String provider) {
}
