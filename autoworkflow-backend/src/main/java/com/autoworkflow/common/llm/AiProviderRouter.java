package com.autoworkflow.common.llm;

import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.integration.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles provider="auto": tries each provider in app.ai.auto-provider-order in turn,
 * stopping at the first success. This is the ONLY place fallback/retry-across-providers
 * logic lives — OpenAiClient/GeminiClient/OpenRouterClient and every execution strategy
 * (AiNodeStrategy, SummarizerStrategy, ClassifierStrategy, AiRouterStrategy) remain
 * completely unaware AUTO mode exists; they just call AiService.chat(provider, request)
 * exactly as before, and AiService transparently delegates to this router when
 * provider="auto".
 *
 * Credential resolution per attempt: strategies normally resolve a user's connected key
 * once, for the single provider they were configured with, before ever building a
 * ChatRequest. That doesn't work for AUTO — nobody knows which provider will actually
 * succeed ahead of time — so this router re-resolves a fresh per-provider key on each
 * attempt using ChatRequest.userId() (set by the strategies specifically so AUTO mode
 * can do this; manual/direct provider calls leave it unused). This preserves the same
 * "user key -> platform key -> unavailable" cascade AUTO mode is supposed to honor per
 * Phase 8 requirement 5, instead of silently only ever using platform keys.
 *
 * Model handling in AUTO mode: any model configured on the incoming request is
 * stripped before trying each provider. A bare model string like "gpt-4o" or
 * "gemini-3.6-flash" is meaningless (or wrong) for a different provider, and there is
 * no reliable way to tell which provider a given model string belongs to without a
 * hardcoded cross-provider catalogue — which Phase 8 explicitly avoids. Each provider
 * falls back to its own app.<provider>.default-model, exactly as it already does for
 * a blank/missing model on a direct (non-AUTO) call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProviderRouter {

    private final AiProviderRegistry registry;
    private final AiAutoModeProperties autoModeProperties;
    private final IntegrationService integrationService;

    public ChatResponse chat(ChatRequest originalRequest) {
        List<String> order = autoModeProperties.getAutoProviderOrder();
        if (order == null || order.isEmpty()) {
            throw new AiException("AI provider is set to \"auto\" but app.ai.auto-provider-order is empty — "
                    + "configure at least one provider in that list.");
        }

        ChatRequest strippedRequest = stripModelForAutoMode(originalRequest);

        List<String> attempted = new ArrayList<>();
        Map<String, String> failureSummaries = new LinkedHashMap<>();

        for (String providerKey : order) {
            AiProvider provider = registry.get(providerKey);
            if (provider == null) {
                log.warn("app.ai.auto-provider-order references unregistered provider '{}' — skipping", providerKey);
                continue;
            }

            attempted.add(providerKey);
            ChatRequest attemptRequest = withResolvedKeyFor(strippedRequest, providerKey);
            try {
                ChatResponse response = provider.chat(attemptRequest);
                boolean fallbackUsed = attempted.size() > 1;
                if (fallbackUsed) {
                    log.info("AUTO provider mode: succeeded with '{}' after trying {}", providerKey, attempted);
                }
                return response.withAutoMetadata(providerKey, fallbackUsed, List.copyOf(attempted));
            } catch (RuntimeException e) {
                String summary = AiFailureClassifier.safeSummary(e);
                failureSummaries.put(providerKey, summary);

                if (!AiFailureClassifier.isRetryable(e)) {
                    log.warn("AUTO provider mode: '{}' failed with a non-retryable error ({}) — stopping, not trying further providers",
                            providerKey, summary);
                    throw e;
                }
                log.warn("AUTO provider mode: '{}' unavailable ({}) — trying next configured provider", providerKey, summary);
            }
        }

        String summaryLine = failureSummaries.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
        throw new AiException("All configured AI providers failed. " + summaryLine);
    }

    /**
     * Re-resolves a per-provider user key for this specific attempt, same cascade
     * strategies use directly: user's connected key -> (left null so the provider
     * client falls back to its own platform key). "Not connected" is expected/normal
     * here, not an error — any OTHER exception from IntegrationService still propagates
     * (a corrupted token or DB error is a real problem, not "try the next provider").
     */
    private ChatRequest withResolvedKeyFor(ChatRequest request, String providerKey) {
        if (request.userId() == null) {
            return request; // no user context (e.g. a direct test call) -> platform key only, as before
        }
        String userKey;
        try {
            userKey = integrationService.getDecryptedAccessToken(request.userId(), providerKey);
        } catch (ResourceNotFoundException e) {
            userKey = null;
        }
        return ChatRequest.builder()
                .messages(request.messages())
                .userApiKey(userKey)
                .model(request.model())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .structuredOutput(request.structuredOutput())
                .userId(request.userId())
                .build();
    }

    private ChatRequest stripModelForAutoMode(ChatRequest original) {
        if (original.model() == null) {
            return original;
        }
        return ChatRequest.builder()
                .messages(original.messages())
                .userApiKey(original.userApiKey())
                .temperature(original.temperature())
                .maxTokens(original.maxTokens())
                .structuredOutput(original.structuredOutput())
                .userId(original.userId())
                .model(null)
                .build();
    }
}
