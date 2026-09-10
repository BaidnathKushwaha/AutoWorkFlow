package com.autoworkflow.common.llm.openrouter;

import java.util.Map;

/**
 * Capability registry for the curated OpenRouter models exposed by AutoWorkFlow.
 * Unknown models intentionally have no structured-output capability declared.
 */
public final class OpenRouterModelCapabilities {

    private static final Map<String, Capability> CAPABILITIES = Map.of(
            "nvidia/nemotron-3-super-120b-a12b:free", new Capability(true, true),
            "google/gemma-4-31b-it:free", new Capability(true, true),
            "google/gemma-4-26b-a4b-it:free", new Capability(true, true),
            "nvidia/nemotron-3-ultra-550b-a55b:free", new Capability(false, true),
            "cohere/north-mini-code:free", new Capability(false, true)
    );

    private OpenRouterModelCapabilities() {
    }

    public static Capability forModel(String modelId) {
        return CAPABILITIES.getOrDefault(modelId, Capability.UNSUPPORTED);
    }

    public record Capability(boolean supportsStructuredOutput, boolean supportsToolCalling) {
        private static final Capability UNSUPPORTED = new Capability(false, false);
    }
}
