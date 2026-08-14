package com.autoworkflow.assistant;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts an embedded ```json ... ``` workflow definition (canvasNodes/canvasEdges)
 * from an LLM's free-text chat response, if present, so the frontend can offer
 * "Create this workflow" alongside the conversational reply.
 */
public final class WorkflowJsonParser {

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    private WorkflowJsonParser() {}

    public static JsonNode extractWorkflowJson(String assistantReply) {
        Matcher matcher = JSON_BLOCK.matcher(assistantReply);
        if (!matcher.find()) return null;
        try {
            JsonNode node = JsonUtils.mapper().readTree(matcher.group(1).trim());
            if (node.has("canvasNodes") || node.has("nodes")) {
                return node;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String stripJsonBlock(String assistantReply) {
        return JSON_BLOCK.matcher(assistantReply).replaceAll("").trim();
    }
}
