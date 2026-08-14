package com.autoworkflow.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateWorkflowRequest(
        String name,
        String description,
        String status,        // DRAFT | ACTIVE | ARCHIVED
        String triggerType,
        JsonNode triggerConfig,
        JsonNode canvasNodes,
        JsonNode canvasEdges
) {}
