package com.autoworkflow.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;

/** Clean, stable shape for a posted Slack message. */
public record SlackMessageResponse(
        String channel,
        String ts,       // Slack's message timestamp — doubles as the message's unique id
        boolean ok
) {
    public static SlackMessageResponse fromApiResponse(JsonNode raw) {
        return new SlackMessageResponse(
                raw.path("channel").asText(),
                raw.path("ts").asText(),
                raw.path("ok").asBoolean(false)
        );
    }
}
