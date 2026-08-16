package com.autoworkflow.user.dto;

import com.autoworkflow.user.AiMode;

import java.util.List;

public record AiPreferenceResponse(
        AiMode mode,
        String provider,
        String model,
        List<ProviderOption> providers
) {

    public record ProviderOption(
            String key,
            String label,
            List<String> models
    ) {
    }
}