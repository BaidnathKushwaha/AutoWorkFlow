package com.autoworkflow.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {
    List<AssistantConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
