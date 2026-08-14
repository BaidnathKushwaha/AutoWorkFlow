package com.autoworkflow.node;

import com.autoworkflow.common.enums.NodeCategory;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.node.dto.NodeDefinitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeDefinitionController {

    private final NodeDefinitionService nodeDefinitionService;

    @GetMapping
    public ApiResponse<List<NodeDefinitionResponse>> getAll() {
        return ApiResponse.success(nodeDefinitionService.getAllActive());
    }

    @GetMapping("/grouped")
    public ApiResponse<Map<String, List<NodeDefinitionResponse>>> getGrouped() {
        return ApiResponse.success(nodeDefinitionService.getGroupedByCategory());
    }

    @GetMapping("/category/{category}")
    public ApiResponse<List<NodeDefinitionResponse>> getByCategory(@PathVariable NodeCategory category) {
        return ApiResponse.success(nodeDefinitionService.getByCategory(category));
    }
}
