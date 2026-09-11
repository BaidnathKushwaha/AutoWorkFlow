package com.autoworkflow.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GmailTriggerStateRepository extends JpaRepository<GmailTriggerState, UUID> {
    Optional<GmailTriggerState> findByWorkflowId(UUID workflowId);
}
