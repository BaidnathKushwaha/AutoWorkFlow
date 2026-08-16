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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a real bug found while auditing model handling: this
 * strategy built ChatRequest WITHOUT ever calling .model(...), so config.model was
 * silently dropped regardless of provider (openai/gemini/openrouter all affected
 * equally — not provider-specific). Fixed by adding .model(config.model) to match
 * the pattern already used in AiNodeStrategy/SummarizerStrategy.
 */
class ClassifierStrategyTest {

    private AiService aiService;
    private ClassifierStrategy strategy;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        strategy = new ClassifierStrategy(aiService);
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("support", "test-model"));
    }

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "classifier", config, input);
    }

    @Test
    void modelFromConfig_isForwardedToChatRequest_forEveryProvider() throws Exception {
        for (String[] providerAndModel : new String[][]{
                {"openai", "gpt-4o"}, {"gemini", "gemini-3.6-flash"}, {"openrouter", "openrouter/free"}}) {
            ObjectNode config = JsonUtils.mapper().createObjectNode();
            config.put("provider", providerAndModel[0]);
            config.put("model", providerAndModel[1]);

            strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello\"}")));

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(aiService).chat(org.mockito.ArgumentMatchers.eq(providerAndModel[0]), captor.capture());
            assertThat(captor.getValue().model()).isEqualTo(providerAndModel[1]);

            org.mockito.Mockito.clearInvocations(aiService);
        }
    }

    @Test
    void unsetProvider_resolvesThroughAiServicesDefaultSentinel_notNullOrStaticConfig() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();

        strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello\"}")));

        verify(aiService).chat(org.mockito.ArgumentMatchers.eq("default"), any(ChatRequest.class));
    }

    @Test
    void modelNotConfigured_forwardsNull_lettingProviderClientUseItsOwnDefault() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        // no "model" key set

        strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello\"}")));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(anyString(), captor.capture());
        assertThat(captor.getValue().model()).isNull();
    }

    // --- Observability: successful output must surface provider/model for the
    // execution console/ConfigPanel (which display outputPayload as-is), and must
    // never contain anything credential-shaped. ---

    @Test
    void successfulOutput_includesProviderAndModel() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class))).thenReturn(new ChatResponse("support", "gpt-4o-mini"));

        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openai");
        config.put("model", "gpt-4o");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello\"}")));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("provider").asText()).isEqualTo("openai");
        // model in the output reflects what the provider actually used (from ChatResponse),
        // not blindly echoing config — they happen to match here since the mock returns it.
        assertThat(result.outputPayload().get("model").asText()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void output_neverContainsApiKeyOrCredentialShapedFields() throws Exception {
        ObjectNode config = JsonUtils.mapper().createObjectNode();
        config.put("provider", "openrouter");
        config.put("model", "openrouter/free");

        var result = strategy.execute(ctx(config, JsonUtils.mapper().readTree("{\"text\":\"hello\"}")));

        JsonNode output = result.outputPayload();
        for (String forbiddenField : new String[]{"apiKey", "userApiKey", "api_key", "token", "accessToken", "credential", "secret"}) {
            assertThat(output.has(forbiddenField))
                    .as("output must never contain a field named '%s'", forbiddenField)
                    .isFalse();
        }
    }
}