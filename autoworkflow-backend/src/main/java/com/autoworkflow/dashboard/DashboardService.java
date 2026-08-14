package com.autoworkflow.dashboard;

import com.autoworkflow.common.enums.ExecutionStatus;
import com.autoworkflow.common.enums.WorkflowStatus;
import com.autoworkflow.dashboard.dto.DashboardStatsResponse;
import com.autoworkflow.dashboard.dto.ExecutionOverviewPoint;
import com.autoworkflow.execution.ExecutionRepository;
import com.autoworkflow.user.UserRepository;
import com.autoworkflow.workflow.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;
    private final UserRepository userRepository;

    public DashboardStatsResponse getStats(UUID userId) {
        long total = workflowRepository.countByUserId(userId);
        long active = workflowRepository.countByUserIdAndStatus(userId, WorkflowStatus.ACTIVE);

        var executions = executionRepository.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(0, 1000));
        long totalExecutions = executions.getTotalElements();
        long failed = executions.getContent().stream().filter(e -> e.getStatus() == ExecutionStatus.FAILED).count();

        long aiRequests = userRepository.findById(userId).map(u -> u.getAiRequestsCount()).orElse(0L);

        return new DashboardStatsResponse(total, active, totalExecutions, failed, aiRequests);
    }

    /** Executions-per-day for the last 7 days, for the Dashboard's Execution Overview chart. */
    public List<ExecutionOverviewPoint> getExecutionOverview(UUID userId) {
        var executions = executionRepository.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(0, 5000)).getContent();
        Instant sevenDaysAgo = Instant.now().minusSeconds(7 * 86400);

        var byDay = executions.stream()
                .filter(e -> e.getStartedAt().isAfter(sevenDaysAgo))
                .collect(Collectors.groupingBy(
                        e -> e.getStartedAt().atZone(ZoneOffset.UTC).getDayOfWeek(),
                        Collectors.counting()));

        return List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                .stream()
                .map(day -> new ExecutionOverviewPoint(
                        day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        byDay.getOrDefault(day, 0L)))
                .collect(Collectors.toList());
    }
}
