package com.autoworkflow.assistant.provider;

import com.autoworkflow.common.llm.ChatMessage;

import java.util.List;

/** Pluggable LLM backend for the AI Assistant. Only OpenAiProvider is implemented
 *  initially, per the original architecture notes; Gemini/Claude are extension points. */
public interface LLMProvider {
    String getName();
    String complete(List<ChatMessage> messages, String userApiKey);
}
