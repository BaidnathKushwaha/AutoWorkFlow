package com.autoworkflow.execution;

import com.autoworkflow.common.enums.ExecutionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists terminal execution state in a separate transaction so an unexpected
 * runtime failure cannot leave the original RUNNING row stranded by transaction rollback.
 */
@Service
public class ExecutionFinalizationService {

    private final ExecutionRepository executionRepository;

    public ExecutionFinalizationService(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedBestEffort(Execution execution, long durationMs, String safeError) {
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setDurationMs(durationMs);
        execution.setFinishedAt(Instant.now());
        execution.setErrorMessage(safeError);
        executionRepository.save(execution);
    }
}
