package com.autoworkflow.workflow;

import com.autoworkflow.common.enums.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Page<Workflow> findByUserId(UUID userId, Pageable pageable);

    Page<Workflow> findByUserIdAndStatus(UUID userId, WorkflowStatus status, Pageable pageable);

    @Query("SELECT w FROM Workflow w WHERE w.userId = :userId AND " +
           "(LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Workflow> searchByUser(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    Optional<Workflow> findByIdAndUserId(UUID id, UUID userId);

    Optional<Workflow> findByWebhookToken(String webhookToken);

    List<Workflow> findByStatusAndDeployedTrue(WorkflowStatus status);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, WorkflowStatus status);

    @Modifying
    @Query("UPDATE Workflow w SET w.executionsCount = w.executionsCount + 1, w.lastRunAt = :now WHERE w.id = :id")
    void incrementExecutionCount(@Param("id") UUID id, @Param("now") Instant now);
}
