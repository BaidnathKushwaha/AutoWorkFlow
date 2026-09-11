package com.autoworkflow.scheduler;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_trigger_states", uniqueConstraints = @UniqueConstraint(columnNames = "workflow_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GmailTriggerState {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "workflow_id", nullable = false, unique = true)
    private UUID workflowId;

    @Column(name = "history_id", nullable = false, length = 80)
    private String historyId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
