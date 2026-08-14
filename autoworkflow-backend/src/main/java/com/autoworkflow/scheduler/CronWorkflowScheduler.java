package com.autoworkflow.scheduler;

import com.autoworkflow.common.enums.TriggeredBy;
import com.autoworkflow.common.enums.WorkflowStatus;
import com.autoworkflow.execution.ExecutionService;
import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.workflow.WorkflowRepository;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Polls every minute for deployed ("ACTIVE") workflows whose trigger is a
 * cron_trigger node and fires the ones whose schedule matches now. A
 * dedicated Quartz JobStore (already on the classpath via
 * spring-boot-starter-quartz) is the natural upgrade path once schedules
 * need to survive across a multi-instance deployment without double-firing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CronWorkflowScheduler {

    private final WorkflowRepository workflowRepository;
    private final ExecutionService executionService;

    private final CronParser cronParser =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    @Scheduled(cron = "0 * * * * *") // every minute, on the minute
    @Async("workflowExecutorPool")
    public void pollAndTrigger() {
        List<Workflow> activeCronWorkflows = workflowRepository.findByStatusAndDeployedTrue(WorkflowStatus.ACTIVE);

        for (Workflow workflow : activeCronWorkflows) {
            if (!"cron_trigger".equals(workflow.getTriggerType()) && !isCronTrigger(workflow)) {
                continue;
            }
            String expression = Optional.ofNullable(workflow.getTriggerConfig())
                    .map(c -> c.path("cronExpression").asText(null))
                    .orElse(null);
            if (expression == null) continue;

            try {
                Cron cron = cronParser.parse(expression);
                ExecutionTime executionTime = ExecutionTime.forCron(cron);
                ZonedDateTime now = ZonedDateTime.now();

                boolean dueThisMinute = executionTime.lastExecution(now)
                        .map(last -> last.isAfter(now.minusMinutes(1)))
                        .orElse(false);

                if (dueThisMinute) {
                    log.info("Firing cron-triggered workflow {} ({})", workflow.getId(), workflow.getName());
                    executionService.execute(workflow.getId(), TriggeredBy.SCHEDULE, JsonUtils.mapper().createObjectNode());
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate cron expression for workflow {}: {}", workflow.getId(), e.getMessage());
            }
        }
    }

    private boolean isCronTrigger(Workflow workflow) {
        if (workflow.getCanvasNodes() == null) return false;
        for (var node : workflow.getCanvasNodes()) {
            if (node.has("type") && "cron_trigger".equals(node.get("type").asText())) return true;
        }
        return false;
    }
}
