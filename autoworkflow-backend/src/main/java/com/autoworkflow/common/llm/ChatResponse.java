package com.autoworkflow.common.llm;

import java.util.List;

/**
 * `actualProvider` / `fallbackUsed` / `attemptedProviders` are ONLY populated by
 * AiProviderRouter (AUTO mode) — every direct provider call (OpenAiClient,
 * GeminiClient, OpenRouterClient) still uses the original 2-arg constructor below
 * unchanged, which leaves these three fields null/false/empty. Strategies check
 * `actualProvider() != null` to know whether a response went through AUTO routing
 * and should report the extra metadata in their own output (see Phase 7/8 output
 * contract), without needing to know anything about routing themselves.
 */
public record ChatResponse(
        String content,
        String model,
        String actualProvider,
        boolean fallbackUsed,
        List<String> attemptedProviders
) {
    /** The original, still-canonical shape for a direct (non-AUTO) provider call. */
    public ChatResponse(String content, String model) {
        this(content, model, null, false, List.of());
    }

    /** Used by AiProviderRouter to attach AUTO-routing metadata to whichever provider actually answered. */
    public ChatResponse withAutoMetadata(String actualProvider, boolean fallbackUsed, List<String> attemptedProviders) {
        return new ChatResponse(content, model, actualProvider, fallbackUsed, attemptedProviders);
    }
}
