package com.autoworkflow.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WorkflowProposal(
        String intent,
        List<WorkflowProposalNode> nodes,
        List<WorkflowProposalEdge> edges
) {
}