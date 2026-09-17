package com.autoworkflow.execution.strategy;

import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoogleSheetsIntegrationStrategyTest {

    @Test
    void emptySheetNamedMappingCreatesHeadersBeforeAppendingData() {
        RecordingApiExecutor api = new RecordingApiExecutor(false);
        IntegrationService integrations = Mockito.mock(IntegrationService.class);
        Mockito.when(integrations.getDecryptedAccessToken(Mockito.any(), Mockito.eq("google_sheets"))).thenReturn("test-token");
        GoogleSheetsIntegrationStrategy strategy = new GoogleSheetsIntegrationStrategy(Mockito.mock(WebClient.Builder.class), integrations, api);

        JsonNode config = config("Sheet1");
        JsonNode payload = JsonUtils.mapper().createObjectNode().put("name", "John").put("email", "john@example.com").put("score", 95);
        NodeExecutionResult result = strategy.execute(context(config, payload));

        assertTrue(result.success());
        assertTrue(result.outputPayload().path("headersCreated").asBoolean());
        assertEquals("Sheet1!A2:C2", result.outputPayload().path("updatedRange").asText());
        assertEquals(1, result.outputPayload().path("updatedRows").asInt());
        assertEquals(3, result.outputPayload().path("updatedColumns").asInt());
        assertEquals(3, result.outputPayload().path("updatedCells").asInt());
        assertEquals(List.of("check target before headers", "create Google Sheets headers", "append row"), api.operations);
    }

    @Test
    void nonEmptySheetNamedMappingDoesNotCreateOrOverwriteHeaders() {
        RecordingApiExecutor api = new RecordingApiExecutor(true);
        IntegrationService integrations = Mockito.mock(IntegrationService.class);
        Mockito.when(integrations.getDecryptedAccessToken(Mockito.any(), Mockito.eq("google_sheets"))).thenReturn("test-token");
        GoogleSheetsIntegrationStrategy strategy = new GoogleSheetsIntegrationStrategy(Mockito.mock(WebClient.Builder.class), integrations, api);

        JsonNode config = config("Sheet1!A1:C10");
        JsonNode payload = JsonUtils.mapper().createObjectNode().put("name", "John").put("email", "john@example.com").put("score", 95);
        NodeExecutionResult result = strategy.execute(context(config, payload));

        assertTrue(result.success());
        assertFalse(result.outputPayload().path("headersCreated").asBoolean());
        assertEquals("Sheet1!A2:C2", result.outputPayload().path("updatedRange").asText());
        assertEquals(List.of("check target before headers", "append row"), api.operations);
    }

    private JsonNode config(String range) {
        return JsonUtils.mapper().createObjectNode()
                .put("operation", "append")
                .put("spreadsheetId", "sheet-id")
                .put("range", range)
                .put("createHeaders", true)
                .put("values", "{\"Name\":\"{{name}}\",\"Email\":\"{{email}}\",\"Score\":\"{{score}}\"}");
    }

    private NodeExecutionContext context(JsonNode config, JsonNode payload) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "sheets", "google_sheets", config, payload);
    }

    private static final class RecordingApiExecutor extends IntegrationApiExecutor {
        private final boolean nonEmpty;
        private final List<String> operations = new ArrayList<>();

        private RecordingApiExecutor(boolean nonEmpty) {
            super(List.of());
            this.nonEmpty = nonEmpty;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String provider, String operation, java.util.function.Supplier<T> call) {
            operations.add(operation);
            JsonNode response;
            if ("check target before headers".equals(operation)) {
                response = JsonUtils.mapper().createObjectNode();
                ((com.fasterxml.jackson.databind.node.ObjectNode) response).set("values", nonEmpty
                        ? JsonUtils.mapper().createArrayNode().add(JsonUtils.mapper().createArrayNode().add("Existing"))
                        : JsonUtils.mapper().createArrayNode());
            } else if ("append row".equals(operation)) {
                response = JsonUtils.mapper().createObjectNode().put("tableRange", "Sheet1!A1:C1");
                ((com.fasterxml.jackson.databind.node.ObjectNode) response).set("updates", JsonUtils.mapper().createObjectNode()
                        .put("updatedRange", "Sheet1!A2:C2").put("updatedRows", 1).put("updatedColumns", 3).put("updatedCells", 3));
            } else {
                response = JsonUtils.mapper().createObjectNode();
            }
            return (T) response;
        }
    }
}
