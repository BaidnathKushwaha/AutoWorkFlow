package com.autoworkflow.common.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProviderRegistry registry;
    private final AiProviderRouter router;

    @Value("${app.ai.default-provider:auto}")
    private String defaultProvider;

    public ChatResponse chat(
            String providerName,
            ChatRequest request
    ) {
        String resolvedProvider =
                (providerName == null || providerName.isBlank())
                        ? defaultProvider
                        : providerName;

        if ("auto".equalsIgnoreCase(resolvedProvider)) {
            return router.chat(request);
        }

        AiProvider provider =
                registry.get(resolvedProvider);

        if (provider == null) {
            throw new AiException(
                    "Unsupported AI provider: "
                            + resolvedProvider
                            + ". Available: "
                            + registry.availableKeys()
            );
        }

        return provider.chat(request);
    }
}