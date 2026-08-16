package com.autoworkflow.common.llm;

import com.autoworkflow.user.AiPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.autoworkflow.user.AiMode;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProviderRegistry registry;
    private final AiProviderRouter router;
    private final AiPreferenceService aiPreferenceService;

    @Value("${app.ai.default-provider:auto}")
    private String defaultProvider;

    public ChatResponse chat(
            String providerName,
            ChatRequest request
    ) {
        String resolvedProvider = providerName;
        ChatRequest resolvedRequest = request;

        if ("default".equalsIgnoreCase(providerName)) {

            AiPreferenceService.ResolvedPreference preference =
                    aiPreferenceService.resolveForUser(
                            request.userId()
                    );

            if (preference.mode() == AiMode.AUTO) {
                return router.chat(request);
            }

            resolvedProvider = preference.provider();

            resolvedRequest = withModel(
                    request,
                    preference.model()
            );
        }

        if (resolvedProvider == null
                || resolvedProvider.isBlank()) {

            resolvedProvider = defaultProvider;
        }

        if ("auto".equalsIgnoreCase(resolvedProvider)) {
            return router.chat(resolvedRequest);
        }

        AiProvider provider = registry.get(resolvedProvider);

        if (provider == null) {
            throw new AiException(
                    "Unsupported AI provider: "
                            + resolvedProvider
                            + ". Available: "
                            + registry.availableKeys()
            );
        }

        return provider.chat(resolvedRequest);
    }

    private ChatRequest withModel(
            ChatRequest request,
            String model
    ) {
        return ChatRequest.builder()
                .messages(request.messages())
                .userApiKey(request.userApiKey())
                .model(model)
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .structuredOutput(request.structuredOutput())
                .userId(request.userId())
                .build();
    }
}