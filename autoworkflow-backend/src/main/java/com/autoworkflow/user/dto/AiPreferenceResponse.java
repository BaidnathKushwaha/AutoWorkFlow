package com.autoworkflow.user.dto;

import java.util.List;

public record AiPreferenceResponse(
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