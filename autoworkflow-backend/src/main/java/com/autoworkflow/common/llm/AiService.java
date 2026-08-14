package com.autoworkflow.common.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * NOTE: provider registration itself moved to AiProviderRegistry (this class used to
 * build its own List<AiProvider> -> Map here) so AiProviderRouter (AUTO mode, see
 * below) can resolve providers from the exact same source of truth instead of a
 * second copy of that map-building logic. The public contract of this class —
 * chat(String providerName, ChatRequest request) — is unchanged.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProviderRegistry registry;
    private final AiProviderRouter router;

    /**
     * The single, externally-configurable place a "default provider" is allowed to live.
     * Execution strategies must NOT hardcode a fallback provider string themselves — they
     * pass through whatever (possibly blank) provider the node's config specifies, and this
     * is where blank resolves to a real provider. Change AI_DEFAULT_PROVIDER (or
     * app.ai.default-provider) to switch the platform-wide default without touching any
     * strategy code.
     */
    @Value("${app.ai.default-provider:gemini}")
    private String defaultProvider;

    public ChatResponse chat(String providerName, ChatRequest request) {
        String resolvedProvider = (providerName == null || providerName.isBlank())
                ? defaultProvider
                : providerName;

        // AUTO mode: manual-provider behavior below is completely unaffected — this is
        // purely an additional branch, not a change to how a named provider resolves.
        if ("auto".equalsIgnoreCase(resolvedProvider)) {
            return router.chat(request);
        }

        AiProvider provider = registry.get(resolvedProvider);
        if (provider == null) {
            throw new AiException("Unsupported AI provider: " + resolvedProvider + ". Available: " + registry.availableKeys());
        }
        return provider.chat(request);
    }
}
