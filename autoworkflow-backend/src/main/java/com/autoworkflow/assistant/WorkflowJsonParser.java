package com.autoworkflow.assistant;

import com.autoworkflow.assistant.dto.WorkflowProposal;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorkflowJsonParser {

    private static final Pattern JSON_BLOCK =
            Pattern.compile(
                    "```json\\s*([\\s\\S]*?)```",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS =
            Set.of("answer", "workflowProposal");

    private WorkflowJsonParser() {
    }

    public static ParsedAssistantResponse parse(String assistantReply) {
        if (assistantReply == null || assistantReply.isBlank()) {
            throw new IllegalArgumentException(
                    "Assistant provider returned an empty response."
            );
        }

        String json = extractJsonEnvelope(assistantReply);

        try {
            JsonNode root = JsonUtils.mapper().readTree(json);

            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(
                        "Assistant response must be a JSON object."
                );
            }

            validateTopLevelFields(root);

            JsonNode answerNode = root.get("answer");

            if (answerNode == null
                    || !answerNode.isTextual()
                    || answerNode.asText().isBlank()) {
                throw new IllegalArgumentException(
                        "Assistant response is missing a textual 'answer' field."
                );
            }

            JsonNode proposalNode = root.get("workflowProposal");

            WorkflowProposal proposal = null;

            if (proposalNode != null && !proposalNode.isNull()) {
                if (!proposalNode.isObject()) {
                    throw new IllegalArgumentException(
                            "'workflowProposal' must be an object or null."
                    );
                }

                proposal = JsonUtils.mapper().treeToValue(
                        proposalNode,
                        WorkflowProposal.class
                );
            }

            return new ParsedAssistantResponse(
                    answerNode.asText(),
                    proposal
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Assistant response contains malformed structured JSON.",
                    e
            );
        }
    }

    private static void validateTopLevelFields(JsonNode root) {
        Iterator<String> fields = root.fieldNames();

        while (fields.hasNext()) {
            String field = fields.next();

            if (!ALLOWED_TOP_LEVEL_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Assistant response contains unsupported top-level field: '"
                                + field
                                + "'."
                );
            }
        }
    }

    private static String extractJsonEnvelope(String assistantReply) {
        Matcher matcher = JSON_BLOCK.matcher(assistantReply);

        if (matcher.find()) {
            String json = matcher.group(1).trim();

            if (matcher.find()) {
                throw new IllegalArgumentException(
                        "Assistant response contains multiple JSON blocks."
                );
            }

            if (json.isBlank()) {
                throw new IllegalArgumentException(
                        "Assistant response contains an empty JSON block."
                );
            }

            return json;
        }

        String trimmed = assistantReply.trim();

        if (!trimmed.startsWith("{")
                || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException(
                    "Assistant response does not contain the required structured JSON envelope."
            );
        }

        return trimmed;
    }

    public record ParsedAssistantResponse(
            String answer,
            WorkflowProposal workflowProposal
    ) {
    }
}