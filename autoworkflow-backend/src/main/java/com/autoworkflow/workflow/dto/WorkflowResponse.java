package com.autoworkflow.workflow.dto;

import com.autoworkflow.workflow.Workflow;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String name,
        String description,
        String status,
        boolean deployed,
        String triggerType,
        JsonNode canvasNodes,
        JsonNode canvasEdges,
        Long executionsCount,
        Instant lastRunAt,
        String webhookToken,
        String webhookUrl,
        Instant updatedAt
) {
        public static WorkflowResponse from(Workflow w) {
            return from(w, null);
        }

        /** publicBaseUrl is the externally-reachable origin (e.g. an ngrok URL) used to build webhookUrl. */
        public static WorkflowResponse from(Workflow w, String publicBaseUrl) {
            String webhookUrl = (w.getWebhookToken() != null && publicBaseUrl != null)
                    ? publicBaseUrl.replaceAll("/$", "") + "/api/webhooks/" + w.getWebhookToken()
                    : null;
            return new WorkflowResponse(
                    w.getId(), w.getName(), w.getDescription(), w.getStatus().name(), w.isDeployed(),
                    w.getTriggerType(), w.getCanvasNodes(), w.getCanvasEdges(),
                    w.getExecutionsCount(), w.getLastRunAt(), w.getWebhookToken(), webhookUrl, w.getUpdatedAt()
            );
        }
}
