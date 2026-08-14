package com.autoworkflow.webhook;

import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.common.exception.WorkflowException;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.execution.dto.ExecutionResponse;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoint (no auth) that external systems POST to in order to fire a
 * deployed workflow whose trigger node is type "webhook". The workflow's
 * unique webhook_token in the URL acts as the secret.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WorkflowRepository workflowRepository;
    private final ExecutionService executionService;

    @PostMapping("/{token}")
    public ApiResponse<ExecutionResponse> receive(@PathVariable String token, @RequestBody(required = false) JsonNode body) {
        Workflow workflow = workflowRepository.findByWebhookToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("No workflow found for this webhook URL"));

        if (!workflow.isDeployed()) {
            throw new WorkflowException("This workflow is not deployed. Click Deploy in the builder before pointing GitHub at this URL.");
        }

        JsonNode payload = body != null ? body : JsonUtils.mapper().createObjectNode();
        ExecutionResponse execution = executionService.execute(workflow.getId(), TriggeredBy.WEBHOOK, payload);
        return ApiResponse.success(execution);
    }
}