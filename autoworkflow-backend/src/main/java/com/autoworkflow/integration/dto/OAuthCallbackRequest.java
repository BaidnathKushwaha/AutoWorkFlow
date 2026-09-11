package com.autoworkflow.integration.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCallbackRequest(
        @NotBlank String code,
        @NotBlank String state
) {}
