package com.autoworkflow.assistant;

import com.autoworkflow.assistant.dto.*;
import com.autoworkflow.assistant.provider.LLMProvider;
import com.autoworkflow.common.enums.MessageRole;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.node.NodeDefinitionService;
import com.autoworkflow.user.UserRepository;
import com.autoworkflow.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs the "AI Assistant" page: a ChatGPT-like interface where the user
 * describes an automation in plain English and gets back both a conversational
 * reply and (when applicable) a ready-to-import workflow JSON.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;
    private final LLMProvider llmProvider;
    private final NodeDefinitionService nodeDefinitionService;
    private final IntegrationService integrationService;
    private final UserRepository userRepository;

    @Transactional
    public ChatResponse chat(UUID userId, ChatRequest request) {
        AssistantConversation conversation = request.conversationId() != null
                ? conversationRepository.findById(request.conversationId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Conversation", request.conversationId()))
                : conversationRepository.save(AssistantConversation.builder()
                        .userId(userId)
                        .title(truncateTitle(request.message()))
                        .build());

        // persist user's message
        messageRepository.save(AssistantMessage.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.USER)
                .content(request.message())
                .build());

        List<AssistantMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        List<ChatMessage> llmMessages = new java.util.ArrayList<>();
        llmMessages.add(ChatMessage.system(buildSystemPrompt()));
        history.forEach(m -> llmMessages.add(new ChatMessage(m.getRole().name().toLowerCase(), m.getContent())));

        String userOpenAiKey = tryGetUserOpenAiKey(userId);
        String reply = llmProvider.complete(llmMessages, userOpenAiKey);

        var generatedWorkflow = WorkflowJsonParser.extractWorkflowJson(reply);
        String displayReply = generatedWorkflow != null ? WorkflowJsonParser.stripJsonBlock(reply) : reply;

        AssistantMessage assistantMessage = messageRepository.save(AssistantMessage.builder()
                .conversationId(conversation.getId())
                .role(MessageRole.ASSISTANT)
                .content(displayReply)
                .generatedWorkflowJson(generatedWorkflow)
                .build());

        userRepository.incrementAiRequestsCount(userId);

        return new ChatResponse(conversation.getId(), ChatMessageResponse.from(assistantMessage));
    }

    public List<ConversationSummaryResponse> listConversations(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ConversationSummaryResponse::from)
                .collect(Collectors.toList());
    }

    public List<ChatMessageResponse> getHistory(UUID userId, UUID conversationId) {
        AssistantConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Conversation", conversationId));
        if (!conversation.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    private String tryGetUserOpenAiKey(UUID userId) {
        try {
            return integrationService.getDecryptedAccessToken(userId, "openai");
        } catch (Exception e) {
            return null; // falls back to the platform key
        }
    }

    private String buildSystemPrompt() {
        String availableTypes = nodeDefinitionService.getAllActive().stream()
                .map(n -> n.typeKey() + " (" + n.category() + "): " + n.description())
                .collect(Collectors.joining("\n"));

        return """
            You are the AutoWorkflow Assistant, embedded in an AI-powered workflow automation platform.
            Help the user describe and build automation pipelines in plain English.

            When the user describes an automation they want to build, respond conversationally,
            and ALSO include a fenced ```json code block containing a workflow definition shaped like:
            { "canvasNodes": [ { "id": "1", "type": "<node_type_key>", "position": {"x":100,"y":100}, "data": {"label": "..."} } ],
              "canvasEdges": [ { "id": "e1-2", "source": "1", "target": "2" } ] }

            Only use these exact node type keys (id: category - description):
            """ + availableTypes + """

            If the user is just asking a question (not requesting a workflow), reply normally without a JSON block.
            """;
    }

    private String truncateTitle(String message) {
        return message.length() > 60 ? message.substring(0, 60) + "..." : message;
    }
}
