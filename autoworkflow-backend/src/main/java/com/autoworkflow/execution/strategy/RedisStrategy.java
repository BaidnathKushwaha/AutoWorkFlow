package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads/writes a key in the connected Redis instance. Requires a
 * StringRedisTemplate bean (add spring-boot-starter-data-redis + a
 * spring.data.redis.* connection config) once Redis is provisioned;
 * degrades to a clear error until then rather than silently no-op-ing.
 */
@Component
public class RedisStrategy implements NodeStrategy {

    private final StringRedisTemplate redisTemplate; // may be null if Redis isn't configured

    public RedisStrategy(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this.redisTemplate = provider.getIfAvailable();
    }

    @Override public String getTypeKey() { return "redis"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        if (redisTemplate == null) {
            return NodeExecutionResult.failed("Redis is not configured on this deployment. Add spring-boot-starter-data-redis and spring.data.redis.* config.");
        }
        JsonNode config = ctx.getNodeConfig();
        String action = config.path("action").asText("get");
        String key = config.path("key").asText();

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        if ("set".equals(action)) {
            String value = config.path("value").asText(ctx.getInputPayload().toString());
            redisTemplate.opsForValue().set(key, value);
            output.put("set", true);
        } else {
            output.put("value", redisTemplate.opsForValue().get(key));
        }
        return NodeExecutionResult.ok(output);
    }
}
