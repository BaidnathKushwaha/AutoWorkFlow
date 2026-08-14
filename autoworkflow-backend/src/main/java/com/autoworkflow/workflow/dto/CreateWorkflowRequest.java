package com.autoworkflow.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateWorkflowRequest(
        @NotBlank(message = "Workflow name is required") String name,
        String description,
        UUID templateId,   // optional: clone canvas from a template
        String triggerType,
        JsonNode canvasNodes,
        JsonNode canvasEdges
) {}
