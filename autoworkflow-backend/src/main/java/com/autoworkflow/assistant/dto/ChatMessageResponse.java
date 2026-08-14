package com.autoworkflow.assistant.dto;

import com.autoworkflow.assistant.AssistantMessage;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        JsonNode generatedWorkflowJson,
        Instant createdAt
) {
    public static ChatMessageResponse from(AssistantMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getGeneratedWorkflowJson(), m.getCreatedAt());
    }
}
