package com.autoworkflow.user.dto;

import com.autoworkflow.user.AiMode;
import jakarta.validation.constraints.NotNull;

public record AiPreferenceUpdateRequest(

        @NotNull(message = "AI mode is required")
        AiMode mode,

        String provider,

        String model
) {
}