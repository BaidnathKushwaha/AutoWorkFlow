package com.autoworkflow.dashboard;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.dashboard.dto.DashboardStatsResponse;
import com.autoworkflow.dashboard.dto.ExecutionOverviewPoint;
import com.autoworkflow.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsResponse> stats() {
        return ApiResponse.success(dashboardService.getStats(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/execution-overview")
    public ApiResponse<List<ExecutionOverviewPoint>> executionOverview() {
        return ApiResponse.success(dashboardService.getExecutionOverview(currentUserProvider.getCurrentUserId()));
    }
}
