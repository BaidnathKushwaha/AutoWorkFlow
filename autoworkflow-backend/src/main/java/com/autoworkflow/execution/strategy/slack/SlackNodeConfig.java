package com.autoworkflow.execution.strategy.slack;

import com.fasterxml.jackson.databind.JsonNode;

public record SlackNodeConfig(String channel, String message) {
    public static SlackNodeConfig from(JsonNode node, String fallbackMessage) {
        return new SlackNodeConfig(
                node.path("channel").asText(),
                node.path("message").asText(fallbackMessage)
        );
    }
}
