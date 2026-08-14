package com.autoworkflow.assistant.dto;

import com.autoworkflow.assistant.AssistantConversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(UUID id, String title, Instant updatedAt) {
    public static ConversationSummaryResponse from(AssistantConversation c) {
        return new ConversationSummaryResponse(c.getId(), c.getTitle(), c.getUpdatedAt());
    }
}
