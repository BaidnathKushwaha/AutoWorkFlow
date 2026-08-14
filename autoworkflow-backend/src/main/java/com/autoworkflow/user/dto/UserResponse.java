package com.autoworkflow.user.dto;

import com.autoworkflow.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String role,
        String avatarUrl,
        Long aiRequestsCount,
        Instant createdAt,
        boolean hasApiKey,
        String apiKeyLastFour
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getAiRequestsCount(),
                user.getCreatedAt(),
                user.getApiKeyEncrypted() != null,
                user.getApiKeyLastFour()
        );
    }
}
