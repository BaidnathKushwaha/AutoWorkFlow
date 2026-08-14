package com.autoworkflow.template;

import com.autoworkflow.common.exception.ResourceNotFoundException;
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

    public List<TemplateResponse> listActive() {
        return templateRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(TemplateResponse::from)
                .collect(Collectors.toList());
    }

    public TemplateDetailResponse getById(UUID id) {
        return TemplateDetailResponse.from(getOrThrow(id));
    }

    /** Clones a template's canvas into a brand new draft workflow owned by the requesting user. */
    @Transactional
    public WorkflowResponse importAsWorkflow(UUID userId, UUID templateId) {
        Template template = getOrThrow(templateId);

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

    private Template getOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Template", id));
    }
}
