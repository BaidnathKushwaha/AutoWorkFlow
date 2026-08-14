package com.autoworkflow.workflow;

import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.common.response.PageResponse;
import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.execution.dto.ExecutionResponse;
import com.autoworkflow.security.CurrentUserProvider;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ExecutionService executionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResponse<WorkflowSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                workflowService.list(currentUserProvider.getCurrentUserId(), search, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        return ApiResponse.success(
                workflowService.create(currentUserProvider.getCurrentUserId(), request),
                "Workflow created");
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(workflowService.getById(currentUserProvider.getCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkflowResponse> update(@PathVariable UUID id, @RequestBody UpdateWorkflowRequest request) {
        return ApiResponse.success(
                workflowService.update(currentUserProvider.getCurrentUserId(), id, request),
                "Workflow saved");
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<WorkflowResponse> deploy(@PathVariable UUID id) {
        return ApiResponse.success(
                workflowService.deploy(currentUserProvider.getCurrentUserId(), id),
                "Workflow deployed and is now active");
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<WorkflowResponse> toggleActive(@PathVariable UUID id) {
        return ApiResponse.success(workflowService.toggleActive(currentUserProvider.getCurrentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        workflowService.delete(currentUserProvider.getCurrentUserId(), id);
        return ApiResponse.success(null, "Workflow deleted");
    }

    /** Manual "Run" trigger from the Workflows list / Workflow Builder toolbar. */
    @PostMapping("/{id}/trigger")
    public ApiResponse<TriggerRunResponse> trigger(@PathVariable UUID id) {
        UUID userId = currentUserProvider.getCurrentUserId();
        workflowService.getById(userId, id); // ownership check
        com.fasterxml.jackson.databind.node.ObjectNode samplePayload = JsonUtils.mapper().createObjectNode();
        samplePayload.put("text", "Sample text from trigger node for testing workflow execution.");
        samplePayload.put("title", "Sample Test Input Title");
        samplePayload.put("status", "success");
        samplePayload.put("action", "test_run");
        ExecutionResponse execution = executionService.execute(id, TriggeredBy.MANUAL, samplePayload);
        return ApiResponse.success(new TriggerRunResponse(execution.id(), execution.status()));
    }
}
