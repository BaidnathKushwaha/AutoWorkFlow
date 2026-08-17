package com.autoworkflow.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantConversationServiceTest {

    @Mock
    private AssistantConversationRepository conversationRepository;

    @Mock
    private AssistantMessageRepository messageRepository;

    @Mock
    private com.autoworkflow.common.llm.AiService aiService;

    @Mock
    private com.autoworkflow.node.NodeDefinitionService nodeDefinitionService;

    @Mock
    private WorkflowProposalValidator workflowProposalValidator;

    @Mock
    private com.autoworkflow.user.UserRepository userRepository;

    @InjectMocks
    private AssistantService assistantService;

    @Test
    void deleteConversation_deletesOwnedConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AssistantConversation conversation = AssistantConversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("My chat")
                .build();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation));

        assistantService.deleteConversation(userId, conversationId);

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void deleteConversation_rejectsConversationOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AssistantConversation conversation = AssistantConversation.builder()
                .id(conversationId)
                .userId(otherUserId)
                .title("Private chat")
                .build();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation));

        assertThatThrownBy(() ->
                assistantService.deleteConversation(userId, conversationId)
        )
                .isInstanceOf(com.autoworkflow.common.exception.ResourceNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(messageRepository);
    }

    @Test
    void deleteConversation_rejectsMissingConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                assistantService.deleteConversation(userId, conversationId)
        )
                .isInstanceOf(com.autoworkflow.common.exception.ResourceNotFoundException.class);
    }
}
