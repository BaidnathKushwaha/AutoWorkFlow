package com.autoworkflow.template;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.security.CurrentUserProvider;
import com.autoworkflow.template.dto.TemplateDetailResponse;
import com.autoworkflow.template.dto.TemplateResponse;
import com.autoworkflow.workflow.dto.WorkflowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list() {
        return ApiResponse.success(templateService.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateDetailResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(templateService.getById(id));
    }

    @PostMapping("/{id}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkflowResponse> importTemplate(@PathVariable UUID id) {
        return ApiResponse.success(
                templateService.importAsWorkflow(currentUserProvider.getCurrentUserId(), id),
                "Template imported as a new draft workflow");
    }
}
