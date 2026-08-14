package com.autoworkflow.common.llm;

public interface AiProvider {

    /** e.g. "openai", "gemini" — must match what AiService.chat(providerName, ...) is called with. */
    String key();

    ChatResponse chat(ChatRequest request);

}
