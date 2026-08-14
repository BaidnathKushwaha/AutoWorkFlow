package com.autoworkflow.workflow;

import com.autoworkflow.common.enums.WorkflowStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Column(name = "is_deployed", nullable = false)
    @Builder.Default
    private boolean deployed = false;

    @Column(name = "trigger_type", length = 60)
    private String triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_config", columnDefinition = "jsonb")
    private JsonNode triggerConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_nodes", columnDefinition = "jsonb", nullable = false)
    private JsonNode canvasNodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_edges", columnDefinition = "jsonb", nullable = false)
    private JsonNode canvasEdges;

    @Column(name = "executions_count", nullable = false)
    @Builder.Default
    private Long executionsCount = 0L;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "webhook_token", unique = true, length = 64)
    private String webhookToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
