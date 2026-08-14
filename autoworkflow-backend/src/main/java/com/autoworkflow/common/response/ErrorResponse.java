package com.autoworkflow.common.response;

import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class ErrorResponse {
    private final boolean success = false;
    private final String message;
    private final String path;
    private final int status;
    private final Instant timestamp;
    private final List<String> details;

    public ErrorResponse(String message, String path, int status, List<String> details) {
        this.message = message;
        this.path = path;
        this.status = status;
        this.timestamp = Instant.now();
        this.details = details;
    }
}
