package com.autoworkflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank @Email(message = "A valid email is required") String email
) {}
