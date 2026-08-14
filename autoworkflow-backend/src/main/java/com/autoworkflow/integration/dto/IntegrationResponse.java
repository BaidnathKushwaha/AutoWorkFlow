package com.autoworkflow.integration.dto;

import com.autoworkflow.integration.Integration;

import java.time.Instant;
import java.util.List;

public record IntegrationResponse(
        String provider,
        String accountLabel,
        String status,
        List<String> scopes,
        Instant lastCheckedAt
) {
    public static IntegrationResponse from(Integration i) {
        return new IntegrationResponse(i.getProvider(), i.getAccountLabel(), i.getStatus().name(), i.getScopes(), i.getLastCheckedAt());
    }

    public static IntegrationResponse disconnectedStub(String provider, List<String> defaultScopes) {
        return new IntegrationResponse(provider, null, "DISCONNECTED", defaultScopes, null);
    }
}
