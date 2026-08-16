package com.autoworkflow.common.llm;

import lombok.Builder;
import java.util.List;
import java.util.UUID;

@Builder
public record ChatRequest(
        List<ChatMessage> messages,
        String userApiKey,
        String model,
        Double temperature,
        Integer maxTokens,
        Boolean structuredOutput,
        /**
         * Only used by AiProviderRouter (AUTO mode) — direct/manual provider calls leave
         * this null and it's ignored. Strategies resolve `userApiKey` themselves for a
         * single known provider before building the request; AUTO mode can't do that
         * ahead of time (it doesn't know which provider will actually succeed), so it
         * needs the raw userId to re-resolve a fresh per-provider key on each attempt.
         * See AiProviderRouter's javadoc.
         */
        UUID userId
) {}
