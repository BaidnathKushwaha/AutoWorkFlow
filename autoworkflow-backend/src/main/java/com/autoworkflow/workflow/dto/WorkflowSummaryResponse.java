package com.autoworkflow.workflow.dto;

import com.autoworkflow.workflow.Workflow;

import java.time.Instant;
import java.util.UUID;

/** Lightweight shape for the Workflows list/grid page (avoids shipping full canvas JSON per card). */
public record WorkflowSummaryResponse(
        UUID id,
        String name,
        String description,
        String status,
        boolean deployed,
        String triggerType,
        int nodesCount,
        Long executionsCount,
        Instant lastRunAt,
        String webhookToken,
        String webhookUrl
) {
    public static WorkflowSummaryResponse from(Workflow w) {
        return from(w, null);
    }

    public static WorkflowSummaryResponse from(Workflow w, String publicBaseUrl) {
        int nodeCount = w.getCanvasNodes() != null && w.getCanvasNodes().isArray() ? w.getCanvasNodes().size() : 0;
        String webhookUrl = (w.getWebhookToken() != null && publicBaseUrl != null)
                ? publicBaseUrl.replaceAll("/$", "") + "/api/webhooks/" + w.getWebhookToken()
                : null;
        return new WorkflowSummaryResponse(
                w.getId(), w.getName(), w.getDescription(), w.getStatus().name(), w.isDeployed(),
                w.getTriggerType(), nodeCount, w.getExecutionsCount(), w.getLastRunAt(), w.getWebhookToken(), webhookUrl
        );
    }
}