package com.autoworkflow.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AiPreferenceUpdateRequest(
        @NotBlank(message = "AI provider is required")
        String provider,

        String model
) {
}