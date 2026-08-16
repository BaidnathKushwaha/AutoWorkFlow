package com.autoworkflow.execution.strategy;

import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import com.autoworkflow.execution.engine.NodeExecutionContext;
import com.autoworkflow.execution.engine.NodeExecutionResult;
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

/**
 * Covers Phase 26/27's required SummarizerStrategy scenarios using a mocked AiService,
 * so this suite runs with ZERO Gemini/OpenAI API calls and zero API credits spent.
 */
class SummarizerStrategyTest {

    private AiService aiService;
    private IntegrationService integrationService;
    private SummarizerStrategy strategy;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        integrationService = mock(IntegrationService.class);
        strategy = new SummarizerStrategy(aiService, integrationService);
        when(aiService.chat(anyString(), any(ChatRequest.class)))
                .thenReturn(new ChatResponse("Test summary", "test-model"));
    }

    private NodeExecutionContext ctx(JsonNode config, JsonNode input) {
        return new NodeExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "node-1", "summarizer", config, input);
    }

    private ObjectNode config(String provider, String textField, String inputText) {
        ObjectNode c = JsonUtils.mapper().createObjectNode();
        if (provider != null) c.put("provider", provider);
        if (textField != null) c.put("textField", textField);
        if (inputText != null) c.put("inputText", inputText);
        return c;
    }

    @Test
    void usesTextFieldFromPayload() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"This is a test document.\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "text", null), input));

        assertThat(result.success()).isTrue();
        assertThat(result.outputPayload().get("summary").asText()).isEqualTo("Test summary");
        assertThat(result.outputPayload().get("provider").asText()).isEqualTo("gemini");

        // Verify the AiService actually received the extracted text (via the prompt), not raw JSON.
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(eq_("gemini"), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("This is a test document.");
    }

    @Test
    void directInputTextTakesPriorityOverTextField() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"payload text\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "text", "explicit override"), input));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(eq_("gemini"), captor.capture());
        assertThat(captor.getValue().messages().get(1).content()).contains("explicit override");
    }

    @Test
    void fallsBackToPayloadTextWhenTextFieldNotConfigured() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"from webhook payload.text\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", null, null), input));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(eq_("gemini"), captor.capture());
        assertThat(captor.getValue().messages().get(1).content()).contains("from webhook payload.text");
    }

    @Test
    void nestedAndArrayIndexTextFieldPathsResolve() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"commits\":[{\"message\":\"Add important note\"}]}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "commits.0.message", null), input));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(eq_("gemini"), captor.capture());
        assertThat(captor.getValue().messages().get(1).content()).contains("Add important note");
    }

    @Test
    void missingText_failsWithClearErrorInsteadOfSerializingRawJson() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"unrelated\":\"field\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", null, null), input));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("no text");
        verifyNoInteractions(aiService); // must not even attempt the call
    }

    @Test
    void allowRawFallback_whenEnabled_stillProducesASummaryInsteadOfFailing() throws Exception {
        ObjectNode c = config("gemini", null, null);
        c.put("allowRawFallback", true);
        JsonNode input = JsonUtils.mapper().readTree("{\"unrelated\":\"field\"}");

        NodeExecutionResult result = strategy.execute(ctx(c, input));

        assertThat(result.success()).isTrue();
    }

    @Test
    void unsetProvider_resolvesThroughAiServicesDefaultSentinel_notNullOrStaticConfig() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");

        strategy.execute(ctx(config(null, "text", null), input));

        verify(aiService).chat(org.mockito.ArgumentMatchers.eq("default"), any(ChatRequest.class));
        verifyNoInteractions(integrationService);
    }

    @Test
    void providerFailure_propagatesAsNodeFailure_notSwallowed() throws Exception {
        when(aiService.chat(anyString(), any(ChatRequest.class)))
                .thenThrow(new com.autoworkflow.common.llm.AiException("Gemini quota exceeded"));

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello\"}");

        org.junit.jupiter.api.Assertions.assertThrows(
                com.autoworkflow.common.llm.AiException.class,
                () -> strategy.execute(ctx(config("gemini", "text", null), input)));
    }

    @Test
    void integrationNotConnected_fallsBackToPlatformKeySilently() throws Exception {
        // The ONE case resolveUserKey is allowed to swallow: the user simply hasn't
        // connected this provider yet. Falls through to AiService's platform-key path.
        when(integrationService.getDecryptedAccessToken(any(), eq_("gemini")))
                .thenThrow(new com.autoworkflow.common.exception.ResourceNotFoundException(
                        "No connected gemini integration for this user."));

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "text", null), input));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiService).chat(eq_("gemini"), captor.capture());
        assertThat(captor.getValue().userApiKey()).isNull(); // fell through to platform key, not a real user key
    }

    @Test
    void unexpectedIntegrationError_propagatesInsteadOfBeingTreatedAsNotConnected() throws Exception {
        // A corrupted token / DB error / anything other than "not connected" must NOT be
        // silently swallowed and re-labelled as "fell back to platform key" — that would
        // hide a real backend problem. This is the fix for the previous catch(Exception).
        when(integrationService.getDecryptedAccessToken(any(), eq_("gemini")))
                .thenThrow(new IllegalStateException("Failed to decrypt stored token"));

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello\"}");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> strategy.execute(ctx(config("gemini", "text", null), input)));
        verifyNoInteractions(aiService); // must fail before ever attempting the AI call
    }

    @Test
    void outputStructureContainsSummaryProviderModelAndInputText() throws Exception {
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "text", null), input));

        JsonNode out = result.outputPayload();
        assertThat(out.has("summary")).isTrue();
        assertThat(out.has("provider")).isTrue();
        assertThat(out.has("model")).isTrue();
        assertThat(out.has("inputText")).isTrue();
    }

    @Test
    void output_neverLeaksTheResolvedApiKey_evenThoughItWasUsedForTheRequest() throws Exception {
        when(integrationService.getDecryptedAccessToken(any(), anyString()))
                .thenReturn("super-secret-user-key-must-not-leak");

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("openrouter", "text", null), input));

        String outputJson = result.outputPayload().toString();
        assertThat(outputJson).doesNotContain("super-secret-user-key-must-not-leak");
        for (String forbiddenField : new String[]{"apiKey", "userApiKey", "api_key", "token", "accessToken", "credential", "secret"}) {
            assertThat(result.outputPayload().has(forbiddenField))
                    .as("output must never contain a field named '%s'", forbiddenField)
                    .isFalse();
        }
    }

    @Test
    void autoModeMetadata_surfacedInOutput_whenAiServiceReturnsIt() throws Exception {
        // AiProviderRouter (via AiService, when provider="auto") attaches actualProvider/
        // fallbackUsed/attemptedProviders onto ChatResponse. This verifies the strategy
        // reads and reports that metadata correctly — it doesn't matter to this test HOW
        // AiService produced it, only that the strategy surfaces it as documented.
        when(aiService.chat(anyString(), any(ChatRequest.class)))
                .thenReturn(new ChatResponse("Test summary", "openrouter/free")
                        .withAutoMetadata("openrouter", true, java.util.List.of("gemini", "openrouter")));

        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("auto", "text", null), input));

        JsonNode out = result.outputPayload();
        assertThat(out.get("provider").asText()).isEqualTo("auto");
        assertThat(out.get("actualProvider").asText()).isEqualTo("openrouter");
        assertThat(out.get("fallbackUsed").asBoolean()).isTrue();
        assertThat(out.get("attemptedProviders")).hasSize(2);
    }

    @Test
    void autoModeMetadata_absent_whenNotUsingAutoMode() throws Exception {
        // Direct/manual provider calls leave actualProvider() null -> the strategy must
        // NOT add actualProvider/fallbackUsed/attemptedProviders fields at all in that case.
        JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");
        NodeExecutionResult result = strategy.execute(ctx(config("gemini", "text", null), input));

        JsonNode out = result.outputPayload();
        assertThat(out.has("actualProvider")).isFalse();
        assertThat(out.has("fallbackUsed")).isFalse();
        assertThat(out.has("attemptedProviders")).isFalse();
    }

    @Test
    void maxLength_clampedToValidRange() throws Exception {
        int[][] testCases = {
                { -50, 20 },
                { 0, 20 },
                { 10, 20 },
                { 200, 200 },
                { 15000, 10000 }
        };

        for (int[] testCase : testCases) {
            int inputLength = testCase[0];
            int expectedClamped = testCase[1];

            reset(aiService);
            when(aiService.chat(anyString(), any(ChatRequest.class)))
                    .thenReturn(new ChatResponse("Test summary", "test-model"));

            ObjectNode c = config("gemini", "text", null);
            c.put("maxLength", inputLength);
            JsonNode inputPayload = JsonUtils.mapper().readTree("{\"text\":\"hello world\"}");

            NodeExecutionResult result = strategy.execute(ctx(c, inputPayload));
            assertThat(result.success()).isTrue();

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(aiService).chat(eq_("gemini"), captor.capture());
            String prompt = captor.getValue().messages().get(1).content();
            assertThat(prompt).contains("within approximately " + expectedClamped + " characters");
        }
    }

    @Test
    void modelFromConfig_isForwardedToChatRequest_forEveryProvider() throws Exception {
        for (String[] providerAndModel : new String[][]{
                {"openai", "gpt-4o"}, {"gemini", "gemini-3.6-flash"}, {"openrouter", "openrouter/free"}}) {
            ObjectNode c = config(providerAndModel[0], "text", null);
            c.put("model", providerAndModel[1]);
            JsonNode input = JsonUtils.mapper().readTree("{\"text\":\"hello\"}");

            strategy.execute(ctx(c, input));

            ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
            verify(aiService).chat(eq_(providerAndModel[0]), captor.capture());
            assertThat(captor.getValue().model()).isEqualTo(providerAndModel[1]);

            clearInvocations(aiService);
        }
    }

    // Small helper so `verify(aiService).chat(eq_("gemini"), ...)` reads cleanly without
    // clashing with Mockito's own static `eq` (kept local & explicit to avoid ambiguity).
    private static String eq_(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}