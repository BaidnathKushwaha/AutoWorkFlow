package com.autoworkflow.workflow.dto;

import java.util.UUID;

public record TriggerRunResponse(UUID executionId, String status) {}
