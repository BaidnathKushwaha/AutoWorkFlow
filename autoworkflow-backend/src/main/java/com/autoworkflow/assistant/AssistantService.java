package com.autoworkflow.assistant;

import com.autoworkflow.assistant.dto.*;
import com.autoworkflow.common.enums.MessageRole;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.common.llm.AiService;
import com.autoworkflow.common.llm.ChatMessage;
import com.autoworkflow.node.NodeDefinitionService;
import com.autoworkflow.user.UserRepository;
import com.autoworkflow.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;
    private final AiService aiService;
    private final NodeDefinitionService nodeDefinitionService;
    private final WorkflowProposalValidator workflowProposalValidator;
    private final UserRepository userRepository;

    @Transactional
    public ChatResponse chat(UUID userId, ChatRequest request) {
        AssistantConversation conversation = resolveConversation(userId, request);

        messageRepository.save(
                AssistantMessage.builder()
                        .conversationId(conversation.getId())
                        .role(MessageRole.USER)
                        .content(request.message())
                        .build()
        );
        touchConversation(conversation);

        List<AssistantMessage> history =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        List<ChatMessage> llmMessages = new ArrayList<>();
        llmMessages.add(ChatMessage.system(buildSystemPrompt()));

        history.forEach(message ->
                llmMessages.add(
                        new ChatMessage(
                                message.getRole().name().toLowerCase(),
                                message.getContent()
                        )
                )
        );

        com.autoworkflow.common.llm.ChatRequest llmRequest =
                com.autoworkflow.common.llm.ChatRequest.builder()
                        .messages(llmMessages)
                        .structuredOutput(true)
                        .userId(userId)
                        .build();

        var providerResponse = aiService.chat("default", llmRequest);

        WorkflowJsonParser.ParsedAssistantResponse parsedResponse;

        try {
            parsedResponse = WorkflowJsonParser.parse(providerResponse.content());
        } catch (IllegalArgumentException e) {
            AssistantMessage assistantMessage =
                    messageRepository.save(
                            AssistantMessage.builder()
                                    .conversationId(conversation.getId())
                                    .role(MessageRole.ASSISTANT)
                                    .content("I could not safely interpret the AI response.")
                                    .build()
                    );
            touchConversation(conversation);
            userRepository.incrementAiRequestsCount(userId);

            return new ChatResponse(
                    conversation.getId(),
                    ChatMessageResponse.from(
                            assistantMessage,
                            null,
                            WorkflowProposalValidation.invalid(safeErrorMessage(e))
                    )
            );
        }

        WorkflowProposal proposal = parsedResponse.workflowProposal();
        WorkflowProposalValidation validation = null;
        JsonNode validatedWorkflowJson = null;

        if (proposal != null) {
            validation = workflowProposalValidator.validate(proposal);
            if (validation.valid()) {
                validatedWorkflowJson = convertProposalToPersistedWorkflow(proposal);
            }
        }

        AssistantMessage assistantMessage =
                messageRepository.save(
                        AssistantMessage.builder()
                                .conversationId(conversation.getId())
                                .role(MessageRole.ASSISTANT)
                                .content(parsedResponse.answer())
                                .generatedWorkflowJson(validatedWorkflowJson)
                                .build()
                );
        touchConversation(conversation);

        userRepository.incrementAiRequestsCount(userId);

        return new ChatResponse(
                conversation.getId(),
                ChatMessageResponse.from(
                        assistantMessage,
                        proposal != null && validation != null && validation.valid()
                                ? proposal
                                : null,
                        validation
                )
        );
    }

    public List<ConversationSummaryResponse> listConversations(UUID userId) {
        return conversationRepository
                .findTop5ByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ConversationSummaryResponse::from)
                .collect(Collectors.toList());
    }

    public List<ChatMessageResponse> getHistory(UUID userId, UUID conversationId) {
        AssistantConversation conversation = findOwnedConversation(userId, conversationId);

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        AssistantConversation conversation = findOwnedConversation(userId, conversationId);
        conversationRepository.delete(conversation);
    }

    private AssistantConversation resolveConversation(UUID userId, ChatRequest request) {
        if (request.conversationId() == null) {
            return conversationRepository.save(
                    AssistantConversation.builder()
                            .userId(userId)
                            .title(truncateTitle(request.message()))
                            .build()
            );
        }

        return findOwnedConversation(userId, request.conversationId());
    }

    private AssistantConversation findOwnedConversation(UUID userId, UUID conversationId) {
        AssistantConversation conversation =
                conversationRepository.findById(conversationId)
                        .orElseThrow(() ->
                                ResourceNotFoundException.of("Conversation", conversationId)
                        );

        if (!conversation.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }

        return conversation;
    }

    private void touchConversation(AssistantConversation conversation) {
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    private String buildSystemPrompt() {
        String availableTypes =
                nodeDefinitionService.getAllActive()
                        .stream()
                        .map(n -> n.typeKey() + " (" + n.category() + "): " + n.description())
                        .collect(Collectors.joining("\n"));

        return """
            You are the AutoWorkflow Assistant, embedded in an AI-powered workflow automation platform.

            Respond ONLY with one JSON object.

            Normal conversation:

            {
              "answer": "Your conversational response.",
              "workflowProposal": null
            }

            Workflow request:

            {
              "answer": "A concise explanation of the proposed workflow.",
              "workflowProposal": {
                "intent": "The automation intent.",
                "nodes": [
                  {
                    "id": "node-1",
                    "type": "exact_node_type_key",
                    "configuration": {
                      "label": "Human readable node label"
                    }
                  }
                ],
                "edges": [
                  {
                    "id": "edge-1",
                    "source": "node-1",
                    "target": "node-2",
                    "configuration": {}
                  }
                ]
              }
            }

            Rules:

            1. workflowProposal must be null for normal questions.
            2. Use only the exact node types listed below.
            3. Never invent node types.
            4. Node IDs must be unique.
            5. Edge IDs must be unique.
            6. Every edge source and target must reference an existing node.
            7. All node-specific settings belong inside configuration.
            8. Never include API keys, access tokens, passwords, secrets,
               authorization credentials, OAuth credentials, JWT secrets,
               database passwords, or other sensitive credentials.
            9. Never invent credentials.
            10. Do not execute workflows.
            11. workflowProposal is only a proposal for user review.
            12. Do not use canvasNodes or canvasEdges.
            13. Do not add additional top-level fields.
            14. Do not use Markdown code fences.
            15. If required information cannot safely be determined,
                explain the missing information in answer and set
                workflowProposal to null.

            Available node types:

            """ + availableTypes;
    }

    private JsonNode convertProposalToPersistedWorkflow(WorkflowProposal proposal) {
        var workflow = JsonUtils.mapper().createObjectNode();
        workflow.set("canvasNodes", convertNodes(proposal));
        workflow.set("canvasEdges", convertEdges(proposal));
        return workflow;
    }

    private JsonNode convertNodes(WorkflowProposal proposal) {
        var nodes = JsonUtils.mapper().createArrayNode();
        int index = 0;

        for (WorkflowProposalNode node : proposal.nodes()) {
            var canvasNode = nodes.addObject();
            canvasNode.put("id", node.id());
            canvasNode.put("type", node.type());

            var position = canvasNode.putObject("position");
            position.put("x", 100 + (index % 4) * 300);
            position.put("y", 100 + (index / 4) * 180);

            canvasNode.set("data", node.configuration());
            index++;
        }

        return nodes;
    }

    private JsonNode convertEdges(WorkflowProposal proposal) {
        var edges = JsonUtils.mapper().createArrayNode();

        for (WorkflowProposalEdge edge : proposal.edges()) {
            var canvasEdge = edges.addObject();
            canvasEdge.put("id", edge.id());
            canvasEdge.put("source", edge.source());
            canvasEdge.put("target", edge.target());

            if (edge.configuration() != null && !edge.configuration().isNull()) {
                canvasEdge.set("data", edge.configuration());
            }
        }

        return edges;
    }

    private String truncateTitle(String message) {
        return message.length() > 60
                ? message.substring(0, 60) + "..."
                : message;
    }

    private String safeErrorMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "Malformed structured assistant response.";
        }
        return e.getMessage();
    }
}
