package com.autoworkflow.template;

import com.autoworkflow.common.dto.StringListConverter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "category", length = 80) private String category;
    @Column(name = "difficulty", length = 40) private String difficulty;
    @Convert(converter = StringListConverter.class)
    @Column(name = "integration_requirements", columnDefinition = "text")
    private List<String> integrationRequirements;
    @Column(name = "trigger_icon_key", length = 60) private String triggerIconKey;
    @Column(name = "target_icon_key", length = 60) private String targetIconKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_nodes", columnDefinition = "jsonb", nullable = false)
    private JsonNode canvasNodes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_edges", columnDefinition = "jsonb", nullable = false)
    private JsonNode canvasEdges;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
}
