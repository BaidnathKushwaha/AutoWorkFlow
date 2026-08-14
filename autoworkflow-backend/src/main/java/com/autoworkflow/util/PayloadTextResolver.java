package com.autoworkflow.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Unified text resolution utility across AI strategies (Summarizer, AI Node, Classifier, AI Router).
 * Resolves input text from node config and payload using a consistent cascade:
 * 1. Literal `inputText` field in config (with {{input}} replacement)
 * 2. `textField` dot-path lookup into payload (supports nested fields & array indices like "commits.0.message")
 * 3. `payload.text` (standard field set by WebhookTriggerStrategy)
 * 4. Standard prose field cascade: message, commits.0.message, body, content, description, summary, title, result
 * 5. Optional raw JSON stringification fallback
 */
public class PayloadTextResolver {

    public static String resolveText(JsonNode config, JsonNode payload, boolean allowRawFallback) {
        if (config != null && config.has("inputText")) {
            String directText = config.path("inputText").asText("").trim();
            if (!directText.isEmpty()) {
                return directText.replace("{{input}}", payload != null && !payload.isNull() ? payload.toString() : "");
            }
        }

        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            if (allowRawFallback) return "(empty input)";
            throw new IllegalArgumentException("Input payload is empty and no direct text input is configured.");
        }

        boolean configHasRawFallback = config != null && config.path("allowRawFallback").asBoolean(false);
        boolean shouldAllowFallback = allowRawFallback || configHasRawFallback;

        // 2. textField path in config (supports dot-paths and array indices)
        if (config != null && config.has("textField")) {
            String fieldPath = config.path("textField").asText("").trim();
            if (!fieldPath.isEmpty()) {
                String v = resolveDotPath(payload, fieldPath);
                if (v != null && !v.isBlank()) return v;
                if (!shouldAllowFallback) {
                    throw new IllegalArgumentException("Configured Payload Field \"" + fieldPath
                            + "\" was not found (or was empty) in the upstream payload.");
                }
            }
        }

        // 3. payload.text (standard field set by WebhookTriggerStrategy)
        String text = payload.path("text").asText("").trim();
        if (!text.isEmpty()) return text;

        // 4. Standard cascade fields
        text = payload.path("message").asText("").trim();
        if (!text.isEmpty()) return text;

        JsonNode commits = payload.path("commits");
        if (commits != null && commits.isArray() && commits.size() > 0) {
            String msg = commits.get(0).path("message").asText("").trim();
            if (!msg.isEmpty()) return msg;
        }

        for (String key : new String[]{"body", "content", "description", "summary", "title", "result"}) {
            text = payload.path(key).asText("").trim();
            if (!text.isEmpty()) return text;
        }

        if (shouldAllowFallback) {
            return payload.toString();
        }

        throw new IllegalArgumentException("No text found in payload. Set 'textField' or 'inputText' in configuration, or enable raw fallback.");
    }

    public static String resolveTextOrRaw(JsonNode config, JsonNode payload) {
        return resolveText(config, payload, true);
    }

    /** Resolves dot-path supporting nested objects and array indices (e.g. "commits.0.message"). */
    public static String resolveDotPath(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) continue;
            if (current == null || current.isMissingNode() || current.isNull()) return null;
            current = (current.isArray() && part.matches("\\d+"))
                    ? current.path(Integer.parseInt(part))
                    : current.path(part);
        }
        return (current == null || current.isMissingNode() || current.isNull()) ? null : current.asText(null);
    }
}
