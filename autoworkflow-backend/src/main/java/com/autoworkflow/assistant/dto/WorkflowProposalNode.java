package com.autoworkflow.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WorkflowProposalNode(
        String id,
        String type,
        JsonNode configuration
) {
}