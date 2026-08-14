package com.autoworkflow.execution.strategy;

import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Same fix/regression as ClassifierStrategyTest: AiRouterStrategy built ChatRequest
 * without ever calling .model(...), silently dropping config.model regardless of
 * provider. Fixed identically.
 */
class AiRouterStrategyTest {

    private AiService aiService;
    private AiRouterStrategy strategy;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        strategy = new AiRouterStrategy(aiService);
    }

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "ai_router", config, input);
    }

    @Test
    void modelFromConfig_isForwardedToChatRequest_forEveryProvider() throws Exception {
        for (String[] providerAndModel : new String[][]{
                {"openai", "gpt-4o"}, {"gemini", "gemini-3.6-flash"}, {"openrouter", "openrouter/free"}}) {
            when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("urgent", "m"));

            ObjectNode config = JsonUtils.mapper().createObjectNode();
            config.put("provider", providerAndModel[0]);
            config.put("model", providerAndModel[1]);
            config.putArray("branches").add("urgent").add("normal");

            strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"help now\"}")));

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(aiService).chat(org.mockito.ArgumentMatchers.eq(providerAndModel[0]), captor.capture());
            assertThat(captor.getValue().model()).isEqualTo(providerAndModel[1]);

            clearInvocations(aiService);
        }
    }

    @Test
    void providerAndModel_bothPreserved_alongsideBranchSelection() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("urgent", "m"));

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        config.put("model", "openrouter/free");
        config.putArray("branches").add("urgent").add("normal");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"help now\"}")));

        assertThat(result.success()).isTrue();
        assertThat(result.branchTaken()).isTrue(); // "urgent" matches branches[0]

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(org.mockito.ArgumentMatchers.eq("openrouter"), captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("openrouter/free");
    }

    // --- Observability: successful output must surface provider/model for the
    // execution console/ConfigPanel (which display outputPayload as-is), and must
    // never contain anything credential-shaped. ---

    @Test
    void successfulOutput_includesProviderAndModel() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("urgent", "gemini-3.6-flash"));

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "gemini");
        config.put("model", "gemini-3.6-flash");
        config.putArray("branches").add("urgent").add("normal");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"help now\"}")));

        assertThat(result.outputPayload().get("provider").asText()).isEqualTo("gemini");
        assertThat(result.outputPayload().get("model").asText()).isEqualTo("gemini-3.6-flash");
    }

    @Test
    void output_neverContainsApiKeyOrCredentialShapedFields() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("urgent", "m"));

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        config.put("model", "openrouter/free");
        config.putArray("branches").add("urgent").add("normal");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"help now\"}")));

        JsonNode output = result.outputPayload();
        for (String forbiddenField : new String[]{"apiKey", "userApiKey", "api_key", "token", "accessToken", "credential", "secret"}) {
            assertThat(output.has(forbiddenField))
                    .as("output must never contain a field named '%s'", forbiddenField)
                    .isFalse();
        }
    }
}
