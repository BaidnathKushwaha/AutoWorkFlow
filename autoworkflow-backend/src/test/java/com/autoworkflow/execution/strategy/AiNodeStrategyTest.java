package com.autoworkflow.execution.strategy;

import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.integration.IntegrationService;
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

/** AiNodeStrategy already called .model(...) correctly — this locks that in for all three providers. */
class AiNodeStrategyTest {

    private AiService aiService;
    private IntegrationService integrationService;
    private AiNodeStrategy strategy;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        integrationService = mock(IntegrationService.class);
        strategy = new AiNodeStrategy(aiService, integrationService);
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("result text", "m"));
    }

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "ai", config, input);
    }

    @Test
    void modelAndProvider_bothPreserved_forEveryProvider() throws Exception {
        for (String[] providerAndModel : new String[][]{
                {"openai", "gpt-4o"}, {"gemini", "gemini-3.6-flash"}, {"openrouter", "openrouter/free"}}) {
            ObjectNode config = JsonUtils.mapper().createObjectNode();
            config.put("provider", providerAndModel[0]);
            config.put("model", providerAndModel[1]);
            config.put("prompt", "Summarize: {{input}}");

            strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello world\"}")));

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(aiService).chat(org.mockito.ArgumentMatchers.eq(providerAndModel[0]), captor.capture());
            assertThat(captor.getValue().model()).isEqualTo(providerAndModel[1]);

            clearInvocations(aiService);
        }
    }

    @Test
    void unsetProvider_resolvesThroughAiServicesDefaultSentinel_notNullOrStaticConfig() throws Exception {
        // No "provider" field at all — this must reach AiService as "default" so the
        // user's account-level AI preference is consulted, not app.ai.default-provider.
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("prompt", "Summarize: {{input}}");

        strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello world\"}")));

        verify(aiService).chat(org.mockito.ArgumentMatchers.eq("default"), any(ChatRequest.class));
    }

    @Test
    void unsetProvider_neverAttemptsAnIntegrationLookupForTheLiteralWordDefault() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("prompt", "Summarize: {{input}}");

        strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello world\"}")));

        verifyNoInteractions(integrationService);
    }

    @Test
    void openrouterProvider_resolvesUserKeyThroughExistingIntegrationService() throws Exception {
        when(integrationService.getDecryptedAccessToken(any(), org.mockito.ArgumentMatchers.eq("openrouter")))
                .thenReturn("user-connected-openrouter-key");

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        config.put("model", "openrouter/free");

        strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hi\"}")));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(anyString(), captor.capture());
        assertThat(captor.getValue().userApiKey()).isEqualTo("user-connected-openrouter-key");
    }

    // --- Observability: successful output must surface provider/model, and must never
    // leak the resolved credential into the node's output (which the execution console/
    // ConfigPanel display as-is, and which persists into execution history). ---

    @Test
    void successfulOutput_includesProviderAndModel() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("result text", "gpt-4o-mini"));

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openai");
        config.put("model", "gpt-4o");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello world\"}")));

        assertThat(result.outputPayload().get("provider").asText()).isEqualTo("openai");
        assertThat(result.outputPayload().get("model").asText()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void output_neverLeaksTheResolvedApiKey_evenThoughItWasUsedForTheRequest() throws Exception {
        when(integrationService.getDecryptedAccessToken(any(), anyString()))
                .thenReturn("super-secret-user-key-must-not-leak");

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        config.put("model", "openrouter/free");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hi\"}")));

        String outputJson = result.outputPayload().toString();
        assertThat(outputJson).doesNotContain("super-secret-user-key-must-not-leak");
        for (String forbiddenField : new String[]{"apiKey", "userApiKey", "api_key", "token", "accessToken", "credential", "secret"}) {
            assertThat(result.outputPayload().has(forbiddenField))
                    .as("output must never contain a field named '%s'", forbiddenField)
                    .isFalse();
        }
    }
}