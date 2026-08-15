package com.autoworkflow.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WorkflowProposalValidation(
        boolean valid,
        List<String> errors
) {

    public static WorkflowProposalValidation success() {
        return new WorkflowProposalValidation(
                true,
                List.of()
        );
    }

    public static WorkflowProposalValidation invalid(
            List<String> errors
    ) {
        return new WorkflowProposalValidation(
                false,
                List.copyOf(errors)
        );
    }

    public static WorkflowProposalValidation invalid(
            String error
    ) {
        return invalid(List.of(error));
    }
}