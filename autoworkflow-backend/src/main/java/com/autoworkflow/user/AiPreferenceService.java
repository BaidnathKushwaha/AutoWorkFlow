package com.autoworkflow.user;

import com.autoworkflow.common.exception.InvalidAiPreferenceException;
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

        AiMode mode = user.getAiMode();

        if (mode == null) {
            mode = AiMode.AUTO;
        }

        if (mode == AiMode.AUTO) {
            return response(
                    AiMode.AUTO,
                    null,
                    null
            );
        }

        String provider = normalizeProvider(user.getAiProvider());
        String model = validateModel(provider, user.getAiModel());

        return response(
                AiMode.SPECIFIC,
                provider,
                model
        );
    }

    @Transactional
    public AiPreferenceResponse update(
            UUID userId,
            AiPreferenceUpdateRequest request
    ) {
        User user = getUser(userId);

        if (request == null || request.mode() == null) {
            throw new InvalidAiPreferenceException(
                    "AI mode is required"
            );
        }

        if (request.mode() == AiMode.AUTO) {
            user.setAiMode(AiMode.AUTO);
            user.setAiProvider(null);
            user.setAiModel(null);

            userRepository.save(user);

            return response(
                    AiMode.AUTO,
                    null,
                    null
            );
        }

        String provider = normalizeProvider(request.provider());
        String model = validateModel(provider, request.model());

        user.setAiMode(AiMode.SPECIFIC);
        user.setAiProvider(provider);
        user.setAiModel(model);

        userRepository.save(user);

        return response(
                AiMode.SPECIFIC,
                provider,
                model
        );
    }

    @Transactional(readOnly = true)
    public ResolvedPreference resolveForUser(UUID userId) {
        if (userId == null) {
            return ResolvedPreference.auto();
        }

        User user = getUser(userId);

        AiMode mode = user.getAiMode();

        if (mode == null || mode == AiMode.AUTO) {
            return ResolvedPreference.auto();
        }

        String provider = normalizeProvider(user.getAiProvider());
        String model = validateModel(provider, user.getAiModel());

        return new ResolvedPreference(
                AiMode.SPECIFIC,
                provider,
                model
        );
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        ResourceNotFoundException.of(
                                "User",
                                userId
                        )
                );
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new InvalidAiPreferenceException(
                    "AI provider is required for SPECIFIC mode"
            );
        }

        String normalized = provider.trim().toLowerCase();

        if (!SUPPORTED_MODELS.containsKey(normalized)) {
            throw new InvalidAiPreferenceException(
                    "Unsupported AI provider: " + provider
            );
        }

        return normalized;
    }

    private String validateModel(
            String provider,
            String model
    ) {
        if (model == null || model.isBlank()) {
            throw new InvalidAiPreferenceException(
                    "AI model is required for SPECIFIC mode"
            );
        }

        String normalized = model.trim();

        if (!SUPPORTED_MODELS
                .get(provider)
                .contains(normalized)) {

            throw new InvalidAiPreferenceException(
                    "Unsupported AI model '"
                            + model
                            + "' for provider '"
                            + provider
                            + "'"
            );
        }

        return normalized;
    }

    private AiPreferenceResponse response(
            AiMode mode,
            String provider,
            String model
    ) {
        List<AiPreferenceResponse.ProviderOption> providers =
                List.of(
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
                mode,
                provider,
                model,
                providers
        );
    }

    public record ResolvedPreference(
            AiMode mode,
            String provider,
            String model
    ) {

        public static ResolvedPreference auto() {
            return new ResolvedPreference(
                    AiMode.AUTO,
                    null,
                    null
            );
        }
    }
}