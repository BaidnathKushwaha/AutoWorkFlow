package com.autoworkflow.integration;

import java.util.List;
import java.util.Map;

/** Static catalog of supported providers and their default permission scopes, matching the Integrations UI cards. */
public final class IntegrationProviderCatalog {

    public static final Map<String, List<String>> DEFAULT_SCOPES = Map.of(
            "github", List.of("Read/Write Repos"),
            "slack", List.of("Post Messages"),
            "openai", List.of("API Access"),
            "gemini", List.of("API Access"),
            "openrouter", List.of("API Access"),
            "gmail", List.of("Read/Send Emails"),
            "google_sheets", List.of("Read/Write Sheets"),
            "notion", List.of("Read/Write Pages"),
            "discord", List.of("Post Messages")
    );

    public static final List<String> ALL_PROVIDERS = List.copyOf(DEFAULT_SCOPES.keySet());

    private IntegrationProviderCatalog() {}
}
