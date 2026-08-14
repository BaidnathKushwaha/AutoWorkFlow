package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/** The generic "HTTP Request" node - calls any URL with any method/headers/body from node config. */
@Component
@RequiredArgsConstructor
public class HttpRequestStrategy implements NodeStrategy {

    private final WebClient.Builder webClientBuilder;

    @Override public String getTypeKey() { return "http_request"; }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        String url = config.path("url").asText();
        HttpMethod method = HttpMethod.valueOf(config.path("method").asText("GET").toUpperCase());

        WebClient client = webClientBuilder.build();
        WebClient.RequestBodySpec request = (WebClient.RequestBodySpec) client.method(method).uri(url);

        if (config.has("headers") && config.get("headers").isObject()) {
            Iterator<Map.Entry<String, JsonNode>> headers = config.get("headers").fields();
            while (headers.hasNext()) {
                Map.Entry<String, JsonNode> h = headers.next();
                request = request.header(h.getKey(), h.getValue().asText());
            }
        }

        JsonNode responseBody;
        if (config.has("body") && !method.equals(HttpMethod.GET)) {
            responseBody = request.bodyValue(config.get("body").toString())
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();
        } else {
            responseBody = request.retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(30)).block();
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("response", responseBody);
        return NodeExecutionResult.ok(output);
    }
}
