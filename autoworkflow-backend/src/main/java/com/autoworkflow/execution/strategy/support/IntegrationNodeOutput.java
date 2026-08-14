package com.autoworkflow.execution.strategy.support;

import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Every integration node strategy returns this exact shape, so downstream
 * nodes (Transform, IF Condition, Merge) and the Execution Detail UI never
 * have to special-case "is this a GitHub output or a Slack output".
 */
public final class IntegrationNodeOutput {

    private IntegrationNodeOutput() {}

    public static ObjectNode success(String provider, String action, String resourceId, String url) {
        ObjectNode out = JsonUtils.mapper().createObjectNode();
        out.put("provider", provider);
        out.put("action", action);
        out.put("success", true);
        if (resourceId != null) out.put("resourceId", resourceId);
        if (url != null) out.put("url", url);
        return out;
    }

    /** For actions with no natural single "resource" (e.g. a query returning rows). */
    public static ObjectNode success(String provider, String action) {
        return success(provider, action, null, null);
    }
}
