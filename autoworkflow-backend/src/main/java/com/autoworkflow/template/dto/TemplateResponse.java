package com.autoworkflow.template.dto;

import com.autoworkflow.template.Template;

import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String description,
        String category,
        String difficulty,
        List<String> integrationRequirements,
        String triggerIconKey,
        String targetIconKey
) {
    public static TemplateResponse from(Template t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getDescription(), t.getCategory(), t.getDifficulty(),
                t.getIntegrationRequirements(), t.getTriggerIconKey(), t.getTargetIconKey());
    }
}
