package com.autoworkflow.assistant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChatRequest(
        @NotBlank(message = "Message cannot be empty") String message,
        UUID conversationId   // null starts a new conversation
) {}
