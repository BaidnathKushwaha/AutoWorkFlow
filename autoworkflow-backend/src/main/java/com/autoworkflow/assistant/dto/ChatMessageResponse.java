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
        WorkflowProposal workflowProposal,
        WorkflowProposalValidation workflowProposalValidation,
        Instant createdAt
) {

    public static ChatMessageResponse from(
            AssistantMessage message
    ) {
        return from(message, null, null);
    }

    public static ChatMessageResponse from(
            AssistantMessage message,
            WorkflowProposal workflowProposal,
            WorkflowProposalValidation workflowProposalValidation
    ) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getGeneratedWorkflowJson(),
                workflowProposal,
                workflowProposalValidation,
                message.getCreatedAt()
        );
    }
}