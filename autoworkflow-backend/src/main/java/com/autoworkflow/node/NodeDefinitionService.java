package com.autoworkflow.node;

import com.autoworkflow.common.enums.NodeCategory;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.node.dto.NodeDefinitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs the "Node Marketplace" page and the Workflow Builder's node palette.
 * Both surfaces must read from this single source of truth so the palette
 * never drifts from what the execution engine actually supports.
 */
@Service
@RequiredArgsConstructor
public class NodeDefinitionService {

    private final NodeDefinitionRepository nodeDefinitionRepository;

    @Cacheable("nodeDefinitions")
    public List<NodeDefinitionResponse> getAllActive() {
        return nodeDefinitionRepository.findByActiveTrueOrderByCategoryAscDisplayNameAsc().stream()
                .map(NodeDefinitionResponse::from)
                .collect(Collectors.toList());
    }

    public Map<String, List<NodeDefinitionResponse>> getGroupedByCategory() {
        return getAllActive().stream()
                .collect(Collectors.groupingBy(NodeDefinitionResponse::category));
    }

    public List<NodeDefinitionResponse> getByCategory(NodeCategory category) {
        return nodeDefinitionRepository.findByCategoryAndActiveTrue(category).stream()
                .map(NodeDefinitionResponse::from)
                .collect(Collectors.toList());
    }

    public NodeDefinition getByTypeKeyOrThrow(String typeKey) {
        return nodeDefinitionRepository.findByTypeKey(typeKey)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown node type: " + typeKey));
    }
}
