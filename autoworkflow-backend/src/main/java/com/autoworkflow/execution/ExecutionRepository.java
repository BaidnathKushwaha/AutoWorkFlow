package com.autoworkflow.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    Page<Execution> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);
    Page<Execution> findByWorkflowIdOrderByStartedAtDesc(UUID workflowId, Pageable pageable);
    Optional<Execution> findByIdAndUserId(UUID id, UUID userId);
}
