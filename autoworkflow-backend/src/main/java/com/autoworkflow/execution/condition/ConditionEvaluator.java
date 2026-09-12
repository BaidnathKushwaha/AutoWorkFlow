package com.autoworkflow.execution.condition;

import com.autoworkflow.common.exception.IntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Deterministic, typed and reusable condition evaluator. No executable expressions. */
@Component
public class ConditionEvaluator {
    public static final int MAX_DEPTH = 8;
    private static final Set<String> OPERATORS = Set.of(
            "equals", "not_equals", "contains", "not_contains", "starts_with", "ends_with",
            "greater_than", "greater_than_or_equal", "less_than", "less_than_or_equal",
            "is_empty", "is_not_empty", "exists", "not_exists", "is_true", "is_false"
    );

    public boolean evaluate(JsonNode input, JsonNode condition) {
        return evaluateNode(input == null ? NullNode.getInstance() : input, condition, 0);
    }

    public void validate(JsonNode condition) {
        validateNode(condition, 0);
    }

    private boolean evaluateNode(JsonNode input, JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw config("Condition tree exceeds maximum depth of " + MAX_DEPTH + ".");
        if (node == null || !node.isObject()) throw config("Each condition must be an object.");

        JsonNode conditions = node.get("conditions");
        if (conditions != null) {
            if (!conditions.isArray() || conditions.isEmpty()) throw config("Condition group 'conditions' must be a non-empty array.");
            String logic = node.path("logic").asText("").toUpperCase(Locale.ROOT);
            if (!logic.equals("AND") && !logic.equals("OR")) throw config("Condition group logic must be AND or OR.");
            if (logic.equals("AND")) {
                for (JsonNode child : conditions) if (!evaluateNode(input, child, depth + 1)) return false;
                return true;
            }
            for (JsonNode child : conditions) if (evaluateNode(input, child, depth + 1)) return true;
            return false;
        }

        String operator = node.path("operator").asText("").trim();
        if (!OPERATORS.contains(operator)) throw config("Unsupported condition operator: '" + operator + "'.");
        String field = node.path("field").asText("").trim();
        JsonNode actual = field.isEmpty() ? input : resolvePath(input, field);
        JsonNode expected = node.get("value");

        return switch (operator) {
            case "equals" -> equals(actual, expected);
            case "not_equals" -> !equals(actual, expected);
            case "contains" -> contains(actual, expected);
            case "not_contains" -> !contains(actual, expected);
            case "starts_with" -> stringOp(actual, expected, "starts_with");
            case "ends_with" -> stringOp(actual, expected, "ends_with");
            case "greater_than" -> compare(actual, expected, ">") > 0;
            case "greater_than_or_equal" -> compare(actual, expected, ">=") >= 0;
            case "less_than" -> compare(actual, expected, "<") < 0;
            case "less_than_or_equal" -> compare(actual, expected, "<=") <= 0;
            case "is_empty" -> isEmpty(actual);
            case "is_not_empty" -> !isEmpty(actual);
            case "exists" -> !actual.isMissingNode();
            case "not_exists" -> actual.isMissingNode();
            case "is_true" -> requireBoolean(actual, operator);
            case "is_false" -> !requireBoolean(actual, operator);
            default -> throw config("Unsupported condition operator: '" + operator + "'.");
        };
    }

    private void validateNode(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw config("Condition tree exceeds maximum depth of " + MAX_DEPTH + ".");
        if (node == null || !node.isObject()) throw config("Each condition must be an object.");
        JsonNode conditions = node.get("conditions");
        if (conditions != null) {
            String logic = node.path("logic").asText("").toUpperCase(Locale.ROOT);
            if (!logic.equals("AND") && !logic.equals("OR")) throw config("Condition group logic must be AND or OR.");
            if (!conditions.isArray() || conditions.isEmpty()) throw config("Condition group 'conditions' must be a non-empty array.");
            for (JsonNode child : conditions) validateNode(child, depth + 1);
            return;
        }
        String operator = node.path("operator").asText("").trim();
        if (!OPERATORS.contains(operator)) throw config("Unsupported condition operator: '" + operator + "'.");
        if (Set.of("exists", "not_exists", "is_true", "is_false", "is_empty", "is_not_empty").contains(operator)) return;
        if (!node.has("value")) throw config("Condition operator '" + operator + "' requires a value.");
        if (!node.path("field").asText("").isBlank()) return;
        // Root payload conditions are valid, but an explicit field is preferred for UI-created nodes.
    }

    public JsonNode resolvePath(JsonNode root, String path) {
        if (path == null || path.isBlank()) return root == null ? NullNode.getInstance() : root;
        JsonNode current = root == null ? NullNode.getInstance() : root;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) throw config("Condition field path contains an empty segment: '" + path + "'.");
            if (current == null || current.isMissingNode() || current.isNull()) return current == null ? NullNode.getInstance() : current;
            if (current.isArray()) {
                if (!segment.matches("\\d+")) return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
                int index;
                try { index = Integer.parseInt(segment); } catch (NumberFormatException e) { return com.fasterxml.jackson.databind.node.MissingNode.getInstance(); }
                current = current.path(index);
            } else if (current.isObject()) {
                current = current.path(segment);
            } else {
                return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            }
        }
        return current;
    }

    private boolean equals(JsonNode actual, JsonNode expected) {
        if (actual.isMissingNode()) return expected == null || expected.isNull();
        if (expected == null || expected.isNull()) return actual.isNull();
        if (actual.isNumber() && expected.isNumber()) return actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        if (actual.isTextual() && expected.isTextual()) return actual.textValue().equals(expected.textValue());
        if (actual.isBoolean() && expected.isBoolean()) return actual.booleanValue() == expected.booleanValue();
        if (actual.isNull() && expected.isNull()) return true;
        if (actual.isContainerNode() && expected.isContainerNode()) return actual.equals(expected);
        throw incompatible("equals", actual, expected);
    }

    private boolean contains(JsonNode actual, JsonNode expected) {
        if (actual.isTextual() && expected != null && expected.isTextual()) return actual.textValue().contains(expected.textValue());
        if (actual.isArray()) {
            for (JsonNode item : actual) {
                if (expected != null && equals(item, expected)) return true;
            }
            return false;
        }
        throw incompatible("contains", actual, expected);
    }

    private boolean stringOp(JsonNode actual, JsonNode expected, String op) {
        if (!actual.isTextual() || expected == null || !expected.isTextual()) throw incompatible(op, actual, expected);
        return op.equals("starts_with") ? actual.textValue().startsWith(expected.textValue()) : actual.textValue().endsWith(expected.textValue());
    }

    private int compare(JsonNode actual, JsonNode expected, String op) {
        if (!actual.isNumber() || expected == null || !expected.isNumber()) throw incompatible(op, actual, expected);
        return actual.decimalValue().compareTo(expected.decimalValue());
    }

    private boolean isEmpty(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ||
                (node.isTextual() && node.textValue().isEmpty()) ||
                (node.isArray() && node.isEmpty()) || (node.isObject() && node.isEmpty());
    }

    private boolean requireBoolean(JsonNode node, String op) {
        if (node == null || node.isMissingNode() || !node.isBoolean()) throw incompatible(op, node, BooleanNodeHolder.TRUE);
        return node.booleanValue();
    }

    private IntegrationException incompatible(String op, JsonNode actual, JsonNode expected) {
        return config("Condition operator '" + op + "' received incompatible types: actual=" + typeOf(actual) + ", expected=" + typeOf(expected) + ".");
    }

    private String typeOf(JsonNode n) {
        if (n == null || n.isMissingNode()) return "missing";
        if (n.isNull()) return "null";
        if (n.isTextual()) return "text";
        if (n.isNumber()) return "number";
        if (n.isBoolean()) return "boolean";
        if (n.isArray()) return "array";
        if (n.isObject()) return "object";
        return n.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    private IntegrationException config(String message) { return new IntegrationException(message); }

    private static final class BooleanNodeHolder {
        private static final JsonNode TRUE = com.fasterxml.jackson.databind.node.BooleanNode.TRUE;
    }
}
