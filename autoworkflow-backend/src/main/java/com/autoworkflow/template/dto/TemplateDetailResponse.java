package com.autoworkflow.template.dto;

import com.autoworkflow.template.Template;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record TemplateDetailResponse(
        UUID id,
        String name,
        String description,
        JsonNode canvasNodes,
        JsonNode canvasEdges
) {
    public static TemplateDetailResponse from(Template t) {
        return new TemplateDetailResponse(t.getId(), t.getName(), t.getDescription(), t.getCanvasNodes(), t.getCanvasEdges());
    }
}
