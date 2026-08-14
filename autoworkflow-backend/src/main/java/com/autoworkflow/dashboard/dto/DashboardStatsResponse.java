package com.autoworkflow.dashboard.dto;

public record DashboardStatsResponse(
        long totalWorkflows,
        long activeWorkflows,
        long totalExecutions,
        long failedRuns,
        long aiRequests
) {}
