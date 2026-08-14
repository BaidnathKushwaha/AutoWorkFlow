package com.autoworkflow.node.dto;

import com.autoworkflow.node.NodeDefinition;
import com.fasterxml.jackson.databind.JsonNode;

public record NodeDefinitionResponse(
        String typeKey,
        String displayName,
        String category,
        String description,
        String icon,
        String color,
        JsonNode configSchema,
        String requiresIntegration
) {
    public static NodeDefinitionResponse from(NodeDefinition node) {
        return new NodeDefinitionResponse(
                node.getTypeKey(),
                node.getDisplayName(),
                node.getCategory().name(),
                node.getDescription(),
                node.getIcon(),
                node.getColor(),
                node.getConfigSchema(),
                node.getRequiresIntegration()
        );
    }
}
