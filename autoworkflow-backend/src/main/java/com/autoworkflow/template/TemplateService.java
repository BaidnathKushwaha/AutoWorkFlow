package com.autoworkflow.template;

import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.execution.engine.NodeStrategyRegistry;
import com.autoworkflow.template.dto.TemplateDetailResponse;
import com.autoworkflow.template.dto.TemplateResponse;
import com.autoworkflow.workflow.Workflow;
import com.autoworkflow.common.enums.WorkflowStatus;
import com.autoworkflow.workflow.WorkflowRepository;
import com.autoworkflow.workflow.dto.WorkflowResponse;
import com.autoworkflow.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {
    private final TemplateRepository templateRepository;
    private final WorkflowRepository workflowRepository;
    private final NodeStrategyRegistry nodeStrategyRegistry;

    public List<TemplateResponse> listActive() {
        return templateRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(TemplateResponse::from).collect(Collectors.toList());
    }

    public TemplateDetailResponse getById(UUID id) {
        return TemplateDetailResponse.from(getOrThrow(id));
    }

    @Transactional
    public WorkflowResponse importAsWorkflow(UUID userId, UUID templateId) {
        Template template = getOrThrow(templateId);
        validateTemplateNodes(template);
        Workflow workflow = Workflow.builder()
                .userId(userId)
                .name(template.getName())
                .description(template.getDescription())
                .status(WorkflowStatus.DRAFT)
                .canvasNodes(template.getCanvasNodes())
                .canvasEdges(template.getCanvasEdges())
                .webhookToken(SlugUtils.randomToken(24))
                .build();
        return WorkflowResponse.from(workflowRepository.save(workflow));
    }

    private void validateTemplateNodes(Template template) {
        if (template.getCanvasNodes() == null || !template.getCanvasNodes().isArray() || template.getCanvasNodes().isEmpty()) {
            throw new IllegalStateException("Template '" + template.getName() + "' has no executable nodes.");
        }
        for (var node : template.getCanvasNodes()) {
            String type = node.path("type").asText("");
            if (type.isBlank() || !nodeStrategyRegistry.isRegisteredType(type)) {
                throw new IllegalStateException("Template '" + template.getName() + "' references unsupported node type: " + type);
            }
        }
    }

    private Template getOrThrow(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Template", id));
    }
}
