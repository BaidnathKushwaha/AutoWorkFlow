package com.autoworkflow.assistant.dto;

import com.autoworkflow.assistant.AssistantMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.autoworkflow.util.JsonUtils;

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
        WorkflowProposal workflowProposal = null;
        WorkflowProposalValidation validation = null;

        if (message.getWorkflowProposalJson() != null) {
            workflowProposal =
                    JsonUtils.mapper().convertValue(
                            message.getWorkflowProposalJson(),
                            WorkflowProposal.class
                    );
        }

        if (message.getWorkflowProposalValidationJson() != null) {
            validation =
                    JsonUtils.mapper().convertValue(
                            message.getWorkflowProposalValidationJson(),
                            WorkflowProposalValidation.class
                    );
        }

        return from(
                message,
                workflowProposal,
                validation
        );
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