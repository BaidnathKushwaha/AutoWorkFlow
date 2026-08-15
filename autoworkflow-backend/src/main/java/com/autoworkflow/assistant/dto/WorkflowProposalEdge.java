package com.autoworkflow.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WorkflowProposalEdge(
        String id,
        String source,
        String target,
        JsonNode configuration
) {
}