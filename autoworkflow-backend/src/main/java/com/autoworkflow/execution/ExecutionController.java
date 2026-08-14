package com.autoworkflow.execution;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.common.response.PageResponse;
import com.autoworkflow.execution.dto.ExecutionDetailResponse;
import com.autoworkflow.execution.dto.ExecutionResponse;
import com.autoworkflow.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResponse<ExecutionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(executionService.listForUser(currentUserProvider.getCurrentUserId(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExecutionDetailResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(executionService.getDetail(currentUserProvider.getCurrentUserId(), id));
    }
}
