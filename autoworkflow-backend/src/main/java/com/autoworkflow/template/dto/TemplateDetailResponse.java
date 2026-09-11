package com.autoworkflow.template.dto;

import com.autoworkflow.template.Template;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record TemplateDetailResponse(
        UUID id,
        String name,
        String description,
        String category,
        String difficulty,
        List<String> integrationRequirements,
        JsonNode canvasNodes,
        JsonNode canvasEdges
) {
    public static TemplateDetailResponse from(Template t) {
        return new TemplateDetailResponse(t.getId(), t.getName(), t.getDescription(), t.getCategory(),
                t.getDifficulty(), t.getIntegrationRequirements(), t.getCanvasNodes(), t.getCanvasEdges());
    }
}
