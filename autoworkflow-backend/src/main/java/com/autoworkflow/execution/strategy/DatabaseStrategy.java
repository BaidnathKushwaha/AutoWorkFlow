package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.execution.engine.NodeStrategy;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runs a parameterized, read-only-by-default query against the platform's own
 * database connection. Node config: { "sql": "SELECT ...", "params": {...} }.
 * Intentionally does NOT support arbitrary DDL/DML from user input without an
 * explicit `allowWrite: true` flag, to keep this node from becoming an
 * unrestricted SQL injection surface.
 */
@Component
@RequiredArgsConstructor
public class DatabaseStrategy implements NodeStrategy {

    @PersistenceContext
    private EntityManager entityManager;

    @Override public String getTypeKey() { return "database"; }

    @Override
    @Transactional(readOnly = true)
    public NodeExecutionResult execute(NodeExecutionContext ctx) {
        JsonNode config = ctx.getNodeConfig();
        String sql = config.path("sql").asText();
        boolean allowWrite = config.path("allowWrite").asBoolean(false);

        if (!allowWrite && !sql.trim().toUpperCase().startsWith("SELECT")) {
            return NodeExecutionResult.failed("Only SELECT queries are allowed unless allowWrite=true is set on the node");
        }

        Query query = entityManager.createNativeQuery(sql);
        if (config.has("params") && config.get("params").isObject()) {
            Iterator<Map.Entry<String, JsonNode>> params = config.get("params").fields();
            while (params.hasNext()) {
                Map.Entry<String, JsonNode> p = params.next();
                query.setParameter(p.getKey(), p.getValue().asText());
            }
        }

        ArrayNode rows = JsonUtils.mapper().createArrayNode();
        List<?> results = query.getResultList();
        for (Object row : results) {
            rows.add(JsonUtils.mapper().valueToTree(row));
        }

        ObjectNode output = JsonUtils.mapper().createObjectNode();
        output.set("rows", rows);
        output.put("rowCount", rows.size());
        return NodeExecutionResult.ok(output);
    }
}
