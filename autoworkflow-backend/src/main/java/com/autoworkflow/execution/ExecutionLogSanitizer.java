package com.autoworkflow.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Sanitizes execution log payloads before they are persisted.
 * The sanitizer is deliberately structural: it redacts credential-bearing keys
 * while preserving the shape and non-secret values of the payload.
 */
public final class ExecutionLogSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private ExecutionLogSanitizer() {
    }

    public static JsonNode sanitize(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        return sanitizeNode(payload);
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode copy = node.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = copy.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    field.setValue(copy.textNode(REDACTED));
                } else {
                    field.setValue(sanitizeNode(field.getValue()));
                }
            }
            return copy;
        }

        if (node.isArray()) {
            ArrayNode copy = node.deepCopy();
            for (int i = 0; i < copy.size(); i++) {
                copy.set(i, sanitizeNode(copy.get(i)));
            }
            return copy;
        }

        return node;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);

        return normalized.contains("authorization")
                || normalized.contains("authentication")
                || normalized.contains("api key")
                || normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.contains("bearer")
                || normalized.contains("private key")
                || normalized.contains("webhook secret");
    }
}
