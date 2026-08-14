package com.autoworkflow.node;

import com.autoworkflow.common.enums.NodeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeDefinitionRepository extends JpaRepository<NodeDefinition, UUID> {
    List<NodeDefinition> findByActiveTrueOrderByCategoryAscDisplayNameAsc();
    List<NodeDefinition> findByCategoryAndActiveTrue(NodeCategory category);
    Optional<NodeDefinition> findByTypeKey(String typeKey);
}
