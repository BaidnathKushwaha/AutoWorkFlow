package com.autoworkflow.assistant.provider;

import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatRequest;
import com.autoworkflow.common.llm.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAIProvider implements LLMProvider {

    private final AiService aiService;

    @Override public String getName() { return "openai"; }

    @Override
    public String complete(List<ChatMessage> messages, String userApiKey) {
        ChatResponse response = aiService.chat("openai", ChatRequest.builder()
                .messages(messages)
                .userApiKey(userApiKey)
                .build());
        return response.content();
    }
}

