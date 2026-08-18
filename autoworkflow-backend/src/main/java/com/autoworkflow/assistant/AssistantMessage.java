package com.autoworkflow.assistant;

import com.autoworkflow.common.enums.MessageRole;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generated_workflow_json", columnDefinition = "jsonb")
    private JsonNode generatedWorkflowJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_proposal_json", columnDefinition = "jsonb")
    private JsonNode workflowProposalJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_proposal_validation_json", columnDefinition = "jsonb")
    private JsonNode workflowProposalValidationJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
