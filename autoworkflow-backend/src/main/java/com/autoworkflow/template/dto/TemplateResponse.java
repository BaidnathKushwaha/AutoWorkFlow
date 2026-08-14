package com.autoworkflow.template.dto;

import com.autoworkflow.template.Template;

import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String description,
        String triggerIconKey,
        String targetIconKey
) {
    public static TemplateResponse from(Template t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getDescription(), t.getTriggerIconKey(), t.getTargetIconKey());
    }
}
