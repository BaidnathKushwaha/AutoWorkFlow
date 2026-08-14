package com.autoworkflow.integration.slack;

import jakarta.validation.constraints.NotBlank;

public record PostSlackMessageRequest(
        @NotBlank(message = "Channel is required (e.g. #general or a channel ID)") String channel,
        @NotBlank(message = "Message text is required") String text
) {}
