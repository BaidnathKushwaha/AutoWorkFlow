package com.autoworkflow.assistant.dto;

import com.autoworkflow.assistant.dto.WorkflowProposal;
import com.autoworkflow.assistant.dto.WorkflowProposalEdge;
import com.autoworkflow.assistant.dto.WorkflowProposalNode;
import com.autoworkflow.assistant.dto.WorkflowProposalValidation;
import com.autoworkflow.execution.validation.WorkflowValidationResult;
import com.autoworkflow.execution.validation.WorkflowValidator;
import com.autoworkflow.node.NodeDefinitionService;
import com.autoworkflow.node.dto.NodeDefinitionResponse;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkflowProposalValidator {

    private static final int MAX_NODES = 100;
    private static final int MAX_EDGES = 200;

    private static final String IDENTIFIER_REGEX =
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$";

    private final NodeDefinitionService nodeDefinitionService;
    private final WorkflowValidator workflowValidator;

    public WorkflowProposalValidation validate(
            WorkflowProposal proposal
    ) {
        if (proposal == null) {
            return WorkflowProposalValidation.invalid(
                    "Workflow proposal cannot be null."
            );
        }

        List<String> errors = new ArrayList<>();

        if (proposal.intent() == null
                || proposal.intent().isBlank()) {
            errors.add(
                    "Workflow proposal intent is missing or blank."
            );
        }

        if (proposal.nodes() == null) {
            errors.add(
                    "Workflow proposal nodes are missing."
            );
        } else if (proposal.nodes().isEmpty()) {
            errors.add(
                    "Workflow proposal contains no nodes."
            );
        } else if (proposal.nodes().size() > MAX_NODES) {
            errors.add(
                    "Workflow proposal contains too many nodes. Maximum allowed is "
                            + MAX_NODES
                            + "."
            );
        }

        if (proposal.edges() == null) {
            errors.add(
                    "Workflow proposal edges are missing."
            );
        } else if (proposal.edges().size() > MAX_EDGES) {
            errors.add(
                    "Workflow proposal contains too many edges. Maximum allowed is "
                            + MAX_EDGES
                            + "."
            );
        }

        if (!errors.isEmpty()) {
            return WorkflowProposalValidation.invalid(errors);
        }

        Map<String, NodeDefinitionResponse> activeDefinitions =
                nodeDefinitionService.getAllActive()
                        .stream()
                        .collect(Collectors.toMap(
                                NodeDefinitionResponse::typeKey,
                                Function.identity(),
                                (first, second) -> first
                        ));

        Set<String> nodeIds = new HashSet<>();

        for (WorkflowProposalNode node : proposal.nodes()) {
            if (node == null) {
                errors.add(
                        "Workflow proposal contains a null node."
                );
                continue;
            }

            validateNode(
                    node,
                    activeDefinitions,
                    nodeIds,
                    errors
            );
        }

        Set<String> edgeIds = new HashSet<>();

        for (WorkflowProposalEdge edge : proposal.edges()) {
            if (edge == null) {
                errors.add(
                        "Workflow proposal contains a null edge."
                );
                continue;
            }

            validateEdge(
                    edge,
                    edgeIds,
                    nodeIds,
                    errors
            );
        }

        if (!errors.isEmpty()) {
            return WorkflowProposalValidation.invalid(errors);
        }

        JsonNode canvasNodes =
                toCanvasNodes(proposal.nodes());

        JsonNode canvasEdges =
                toCanvasEdges(proposal.edges());

        WorkflowValidationResult workflowValidation =
                workflowValidator.validateForExecution(
                        canvasNodes,
                        canvasEdges
                );

        if (!workflowValidation.isValid()) {
            return WorkflowProposalValidation.invalid(
                    workflowValidation.error()
            );
        }

        return WorkflowProposalValidation.success();
    }

    private void validateNode(
            WorkflowProposalNode node,
            Map<String, NodeDefinitionResponse> activeDefinitions,
            Set<String> nodeIds,
            List<String> errors
    ) {
        String nodeId = node.id();

        if (nodeId == null || nodeId.isBlank()) {
            errors.add(
                    "Workflow proposal contains a node with a missing or blank ID."
            );
        } else if (!nodeId.matches(IDENTIFIER_REGEX)) {
            errors.add(
                    "Node ID '"
                            + nodeId
                            + "' contains invalid characters or is too long."
            );
        } else if (!nodeIds.add(nodeId)) {
            errors.add(
                    "Duplicate node ID detected: '"
                            + nodeId
                            + "'."
            );
        }

        String type = node.type();

        if (type == null || type.isBlank()) {
            errors.add(
                    "Node '"
                            + safeId(nodeId)
                            + "' has a missing or blank node type."
            );
            return;
        }

        NodeDefinitionResponse definition =
                activeDefinitions.get(type);

        if (definition == null) {
            errors.add(
                    "Unknown or inactive node type: '"
                            + type
                            + "' for node '"
                            + safeId(nodeId)
                            + "'."
            );
            return;
        }

        JsonNode configuration =
                node.configuration();

        if (configuration == null
                || configuration.isNull()
                || !configuration.isObject()) {
            errors.add(
                    "Node '"
                            + safeId(nodeId)
                            + "' must contain an object configuration."
            );
            return;
        }

        validateSensitiveConfiguration(
                nodeId,
                configuration,
                errors
        );

        if (definition.configSchema() != null
                && !definition.configSchema().isNull()) {
            validateAgainstSchema(
                    nodeId,
                    configuration,
                    definition.configSchema(),
                    "$",
                    errors
            );
        }
    }

    private void validateEdge(
            WorkflowProposalEdge edge,
            Set<String> edgeIds,
            Set<String> nodeIds,
            List<String> errors
    ) {
        String edgeId = edge.id();

        if (edgeId == null || edgeId.isBlank()) {
            errors.add(
                    "Workflow proposal contains an edge with a missing or blank ID."
            );
        } else if (!edgeId.matches(IDENTIFIER_REGEX)) {
            errors.add(
                    "Edge ID '"
                            + edgeId
                            + "' contains invalid characters or is too long."
            );
        } else if (!edgeIds.add(edgeId)) {
            errors.add(
                    "Duplicate edge ID detected: '"
                            + edgeId
                            + "'."
            );
        }

        String source = edge.source();
        String target = edge.target();

        if (source == null || source.isBlank()) {
            errors.add(
                    "Edge '"
                            + safeId(edgeId)
                            + "' has a missing or blank source."
            );
        } else if (!nodeIds.contains(source)) {
            errors.add(
                    "Edge '"
                            + safeId(edgeId)
                            + "' references non-existent source node '"
                            + source
                            + "'."
            );
        }

        if (target == null || target.isBlank()) {
            errors.add(
                    "Edge '"
                            + safeId(edgeId)
                            + "' has a missing or blank target."
            );
        } else if (!nodeIds.contains(target)) {
            errors.add(
                    "Edge '"
                            + safeId(edgeId)
                            + "' references non-existent target node '"
                            + target
                            + "'."
            );
        }

        if (edge.configuration() != null
                && !edge.configuration().isNull()
                && !edge.configuration().isObject()) {
            errors.add(
                    "Edge '"
                            + safeId(edgeId)
                            + "' configuration must be an object."
            );
        }
    }

    private void validateSensitiveConfiguration(
            String nodeId,
            JsonNode configuration,
            List<String> errors
    ) {
        scanForSensitiveKeys(
                configuration,
                "$",
                nodeId,
                errors
        );
    }

    private void scanForSensitiveKeys(
            JsonNode node,
            String path,
            String nodeId,
            List<String> errors
    ) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fieldName -> {
                String normalized =
                        fieldName.toLowerCase();

                if (isSensitiveField(normalized)) {
                    errors.add(
                            "Node '"
                                    + safeId(nodeId)
                                    + "' configuration contains a prohibited sensitive field at "
                                    + path
                                    + "."
                                    + fieldName
                                    + "."
                    );
                }

                scanForSensitiveKeys(
                        node.get(fieldName),
                        path + "." + fieldName,
                        nodeId,
                        errors
                );
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                scanForSensitiveKeys(
                        node.get(i),
                        path + "[" + i + "]",
                        nodeId,
                        errors
                );
            }
        }
    }

    private boolean isSensitiveField(String field) {
        return field.contains("api_key")
                || field.contains("apikey")
                || field.contains("access_token")
                || field.contains("accesstoken")
                || field.contains("refresh_token")
                || field.contains("refreshtoken")
                || field.contains("password")
                || field.contains("secret")
                || field.contains("authorization")
                || field.contains("bearer")
                || field.contains("credential");
    }

    private void validateAgainstSchema(
            String nodeId,
            JsonNode value,
            JsonNode schema,
            String path,
            List<String> errors
    ) {
        if (schema == null || schema.isNull()) {
            return;
        }

        JsonNode type = schema.get("type");

        if (type != null
                && type.isTextual()
                && !matchesJsonType(
                        value,
                        type.asText()
                )) {
            errors.add(
                    "Node '"
                            + safeId(nodeId)
                            + "' has invalid configuration type at "
                            + path
                            + ". Expected "
                            + type.asText()
                            + "."
            );

            return;
        }

        JsonNode required =
                schema.get("required");

        if (required != null
                && required.isArray()
                && value.isObject()) {

            for (JsonNode requiredField : required) {
                String fieldName =
                        requiredField.asText();

                if (!value.has(fieldName)
                        || value.get(fieldName).isNull()) {
                    errors.add(
                            "Node '"
                                    + safeId(nodeId)
                                    + "' is missing required configuration field '"
                                    + fieldName
                                    + "'."
                    );
                }
            }
        }

        JsonNode properties =
                schema.get("properties");

        if (value.isObject()
                && properties != null
                && properties.isObject()) {

            JsonNode additionalProperties =
                    schema.get("additionalProperties");

            if (additionalProperties != null
                    && additionalProperties.isBoolean()
                    && !additionalProperties.asBoolean()) {

                value.fieldNames().forEachRemaining(fieldName -> {
                    if (!properties.has(fieldName)) {
                        errors.add(
                                "Node '"
                                        + safeId(nodeId)
                                        + "' contains unsupported configuration field '"
                                        + fieldName
                                        + "'."
                        );
                    }
                });
            }

            properties.fieldNames()
                    .forEachRemaining(fieldName -> {
                        if (value.has(fieldName)
                                && !value.get(fieldName).isNull()) {

                            validateAgainstSchema(
                                    nodeId,
                                    value.get(fieldName),
                                    properties.get(fieldName),
                                    path + "." + fieldName,
                                    errors
                            );
                        }
                    });
        }

        JsonNode enumValues =
                schema.get("enum");

        if (enumValues != null
                && enumValues.isArray()) {

            boolean matches = false;

            for (JsonNode allowed : enumValues) {
                if (allowed.equals(value)) {
                    matches = true;
                    break;
                }
            }

            if (!matches) {
                errors.add(
                        "Node '"
                                + safeId(nodeId)
                                + "' has an unsupported configuration value at "
                                + path
                                + "."
                );
            }
        }
    }

    private boolean matchesJsonType(
            JsonNode value,
            String expectedType
    ) {
        return switch (expectedType) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private JsonNode toCanvasNodes(
            List<WorkflowProposalNode> nodes
    ) {
        ArrayNode canvasNodes =
                JsonUtils.mapper().createArrayNode();

        int index = 0;

        for (WorkflowProposalNode node : nodes) {
            ObjectNode canvasNode =
                    canvasNodes.addObject();

            canvasNode.put(
                    "id",
                    node.id()
            );

            canvasNode.put(
                    "type",
                    node.type()
            );

            ObjectNode position =
                    canvasNode.putObject("position");

            position.put(
                    "x",
                    100 + (index % 4) * 300
            );

            position.put(
                    "y",
                    100 + (index / 4) * 180
            );

            canvasNode.set(
                    "data",
                    node.configuration()
            );

            index++;
        }

        return canvasNodes;
    }

    private JsonNode toCanvasEdges(
            List<WorkflowProposalEdge> edges
    ) {
        ArrayNode canvasEdges =
                JsonUtils.mapper().createArrayNode();

        for (WorkflowProposalEdge edge : edges) {
            ObjectNode canvasEdge =
                    canvasEdges.addObject();

            canvasEdge.put(
                    "id",
                    edge.id()
            );

            canvasEdge.put(
                    "source",
                    edge.source()
            );

            canvasEdge.put(
                    "target",
                    edge.target()
            );

            if (edge.configuration() != null
                    && !edge.configuration().isNull()) {

                canvasEdge.set(
                        "data",
                        edge.configuration()
                );
            }
        }

        return canvasEdges;
    }

    private String safeId(String id) {
        return id == null || id.isBlank()
                ? "<missing>"
                : id;
    }
}