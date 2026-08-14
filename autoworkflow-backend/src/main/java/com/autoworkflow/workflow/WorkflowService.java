package com.autoworkflow.workflow;

import com.autoworkflow.common.enums.WorkflowStatus;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.common.response.PageResponse;
import com.autoworkflow.template.Template;
import com.autoworkflow.template.TemplateRepository;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.util.SlugUtils;
import com.autoworkflow.workflow.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    /**
     * Trigger types that are fired externally over HTTP and therefore need a
     * webhook_token.
     */
    private static final Set<String> WEBHOOK_DRIVEN_TRIGGER_TYPES = Set.of("webhook", "github_event", "github");

    private final WorkflowRepository workflowRepository;
    private final TemplateRepository templateRepository;
    private final com.autoworkflow.execution.validation.WorkflowValidator workflowValidator;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private static boolean isWebhookDriven(String triggerType) {
        return triggerType != null && WEBHOOK_DRIVEN_TRIGGER_TYPES.contains(triggerType.toLowerCase());
    }

    @Transactional
    public WorkflowResponse create(UUID userId, CreateWorkflowRequest request) {
        Workflow.WorkflowBuilder builder = Workflow.builder()
                .userId(userId)
                .name(request.name())
                .description(request.description())
                .status(WorkflowStatus.DRAFT)
                .triggerType(request.triggerType())
                .canvasNodes(
                        request.canvasNodes() != null ? request.canvasNodes() : JsonUtils.mapper().createArrayNode())
                .canvasEdges(
                        request.canvasEdges() != null ? request.canvasEdges() : JsonUtils.mapper().createArrayNode());

        // Only webhook-driven triggers need a webhook_token; it's generated for real at
        // Deploy time too,
        // but we also assign one at Save time so the URL is stable across edits.
        if (isWebhookDriven(request.triggerType())) {
            builder.webhookToken(SlugUtils.randomToken(24));
        }

        if (request.templateId() != null) {
            Template template = templateRepository.findById(request.templateId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Template", request.templateId()));
            builder.canvasNodes(template.getCanvasNodes())
                    .canvasEdges(template.getCanvasEdges())
                    .description(request.description() != null ? request.description() : template.getDescription());
        }

        Workflow saved = workflowRepository.save(builder.build());
        return WorkflowResponse.from(saved, publicBaseUrl);
    }

    public PageResponse<WorkflowSummaryResponse> list(UUID userId, String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Workflow> result;

        if (StringUtils.hasText(search)) {
            result = workflowRepository.searchByUser(userId, search, pageable);
        } else if (StringUtils.hasText(status)) {
            result = workflowRepository.findByUserIdAndStatus(userId, WorkflowStatus.valueOf(status.toUpperCase()),
                    pageable);
        } else {
            result = workflowRepository.findByUserId(userId, pageable);
        }

        return new PageResponse<>(result.map(w -> WorkflowSummaryResponse.from(w, publicBaseUrl)));
    }

    public WorkflowResponse getById(UUID userId, UUID workflowId) {
        return WorkflowResponse.from(getOwnedOrThrow(userId, workflowId), publicBaseUrl);
    }

    @Transactional
    public WorkflowResponse update(UUID userId, UUID workflowId, UpdateWorkflowRequest request) {
        Workflow workflow = getOwnedOrThrow(userId, workflowId);

        boolean deploymentInvalidated = false;

        if (request.name() != null) {
            workflow.setName(request.name());
        }

        if (request.description() != null) {
            workflow.setDescription(request.description());
        }

        if (request.triggerType() != null
                && !request.triggerType().equals(workflow.getTriggerType())) {
            workflow.setTriggerType(request.triggerType());
            deploymentInvalidated = true;
        }

        if (request.triggerConfig() != null) {
            workflow.setTriggerConfig(request.triggerConfig());
        }

        if (request.canvasNodes() != null
                && !request.canvasNodes().equals(workflow.getCanvasNodes())) {
            workflow.setCanvasNodes(request.canvasNodes());
            deploymentInvalidated = true;
        }

        if (request.canvasEdges() != null
                && !request.canvasEdges().equals(workflow.getCanvasEdges())) {
            workflow.setCanvasEdges(request.canvasEdges());
            deploymentInvalidated = true;
        }

        if (workflow.getWebhookToken() == null && isWebhookDriven(workflow.getTriggerType())) {
            workflow.setWebhookToken(SlugUtils.randomToken(24));
        }

        /*
         * Any change to the deployed graph or trigger type invalidates
         * the current deployment.
         */
        if (deploymentInvalidated) {
            workflow.setStatus(WorkflowStatus.DRAFT);
            workflow.setDeployed(false);
        }

        if (request.status() != null && !deploymentInvalidated) {
            WorkflowStatus newStatus = WorkflowStatus.valueOf(request.status().toUpperCase());
            workflow.setStatus(newStatus);

            if (newStatus != WorkflowStatus.ACTIVE) {
                workflow.setDeployed(false);
            }
        }

        return WorkflowResponse.from(
                workflowRepository.save(workflow),
                publicBaseUrl);
    }

    @Transactional
    public WorkflowResponse deploy(UUID userId, UUID workflowId) {
        Workflow workflow = getOwnedOrThrow(userId, workflowId);

        // Deployment-mode validation: a trigger node IS required here (unlike a manual
        // execute() call, see ExecutionService) — this is the one place that guarantee
        // is enforced, so every webhook/schedule-triggered execution downstream can
        // rely
        // on a trigger node having existed at deploy time.
        workflowValidator.validateDeploymentOrThrow(workflow.getCanvasNodes(), workflow.getCanvasEdges());

        // webhook_token is only meaningful for triggers that are fired over HTTP.
        if (isWebhookDriven(workflow.getTriggerType()) && workflow.getWebhookToken() == null) {
            workflow.setWebhookToken(SlugUtils.randomToken(24));
        }

        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setDeployed(true);
        return WorkflowResponse.from(workflowRepository.save(workflow), publicBaseUrl);
    }

    @Transactional
    public WorkflowResponse toggleActive(UUID userId, UUID workflowId) {
        Workflow workflow = getOwnedOrThrow(userId, workflowId);
        if (workflow.getStatus() == WorkflowStatus.ACTIVE) {
            workflow.setStatus(WorkflowStatus.DRAFT);
            workflow.setDeployed(false);
        } else {
            return deploy(userId, workflowId);
        }
        return WorkflowResponse.from(workflowRepository.save(workflow), publicBaseUrl);
    }

    @Transactional
    public void delete(UUID userId, UUID workflowId) {
        Workflow workflow = getOwnedOrThrow(userId, workflowId);
        workflowRepository.delete(workflow);
    }

    Workflow getOwnedOrThrow(UUID userId, UUID workflowId) {
        Workflow workflow = workflowRepository.findByIdAndUserId(workflowId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Workflow", workflowId));
        if (workflow.getWebhookToken() == null && isWebhookDriven(workflow.getTriggerType())) {
            workflow.setWebhookToken(SlugUtils.randomToken(24));
            workflow = workflowRepository.save(workflow);
        }
        return workflow;
    }
}