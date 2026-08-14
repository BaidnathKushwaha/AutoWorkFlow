package com.autoworkflow.assistant.dto;

import java.util.UUID;

public record ChatResponse(UUID conversationId, ChatMessageResponse message) {}
