package com.autoworkflow.user;

import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.user.dto.AiPreferenceResponse;
import com.autoworkflow.user.dto.AiPreferenceUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiPreferenceService {

    private static final String AUTO = "auto";

    /*
     * Backend-owned provider/model catalogue.
     *
     * These values are configuration choices, not secrets.
     *
     * OpenRouter deliberately uses the fixed Gemini 2.5 Flash model rather
     * than openrouter/free because the Assistant requires predictable
     * structured output behavior.
     */
    private static final Map<String, List<String>> SUPPORTED_MODELS = Map.of(
            "openrouter",
            List.of("google/gemini-2.5-flash"),

            "gemini",
            List.of("gemini-3.6-flash"),

            "openai",
            List.of(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "gpt-4-turbo",
                    "gpt-3.5-turbo"
            )
    );

    private static final Map<String, String> PROVIDER_LABELS = Map.of(
            "openrouter", "OpenRouter",
            "gemini", "Gemini",
            "openai", "OpenAI"
    );

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AiPreferenceResponse get(UUID userId) {
        User user = getUser(userId);

        return response(
                normalizeProvider(user.getAiProvider()),
                normalizeModel(
                        normalizeProvider(user.getAiProvider()),
                        user.getAiModel()
                )
        );
    }

    @Transactional
    public AiPreferenceResponse update(
            UUID userId,
            AiPreferenceUpdateRequest request
    ) {
        User user = getUser(userId);

        String provider = normalizeProvider(request.provider());
        String model = normalizeModel(provider, request.model());

        user.setAiProvider(provider);
        user.setAiModel(model);

        userRepository.save(user);

        return response(provider, model);
    }

    /**
     * Resolves the user's configured default for an AI operation.
     *
     * Explicit provider selections (a real provider key like "openai", or the literal
     * "auto") supplied by workflow nodes or callers continue to bypass this method —
     * see AiService.chat(). Only the "default" sentinel routes here, meaning "no
     * explicit override was given; use this user's account-level AI preference."
     */
    @Transactional(readOnly = true)
    public ResolvedPreference resolveForUser(UUID userId) {
        if (userId == null) {
            return new ResolvedPreference(AUTO, null);
        }

        User user = getUser(userId);

        String provider = normalizeProvider(user.getAiProvider());

        return new ResolvedPreference(
                provider,
                normalizeModel(provider, user.getAiModel())
        );
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        ResourceNotFoundException.of("User", userId)
                );
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return AUTO;
        }

        String normalized = provider.trim().toLowerCase();

        if (AUTO.equals(normalized)) {
            return AUTO;
        }

        if (!SUPPORTED_MODELS.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported AI provider: " + provider
            );
        }

        return normalized;
    }

    private String normalizeModel(
            String provider,
            String model
    ) {
        if (AUTO.equals(provider)) {
            return null;
        }

        if (model == null || model.isBlank()) {
            return SUPPORTED_MODELS
                    .get(provider)
                    .get(0);
        }

        String normalized = model.trim();

        if (!SUPPORTED_MODELS
                .get(provider)
                .contains(normalized)) {

            throw new IllegalArgumentException(
                    "Unsupported AI model '" + model
                            + "' for provider '" + provider + "'"
            );
        }

        return normalized;
    }

    private AiPreferenceResponse response(
            String provider,
            String model
    ) {
        List<AiPreferenceResponse.ProviderOption> providers =
                List.of(
                        new AiPreferenceResponse.ProviderOption(
                                AUTO,
                                "Auto",
                                List.of()
                        ),
                        new AiPreferenceResponse.ProviderOption(
                                "openrouter",
                                PROVIDER_LABELS.get("openrouter"),
                                SUPPORTED_MODELS.get("openrouter")
                        ),
                        new AiPreferenceResponse.ProviderOption(
                                "gemini",
                                PROVIDER_LABELS.get("gemini"),
                                SUPPORTED_MODELS.get("gemini")
                        ),
                        new AiPreferenceResponse.ProviderOption(
                                "openai",
                                PROVIDER_LABELS.get("openai"),
                                SUPPORTED_MODELS.get("openai")
                        )
                );

        return new AiPreferenceResponse(
                provider,
                model,
                providers
        );
    }

    public record ResolvedPreference(
            String provider,
            String model
    ) {
    }
}