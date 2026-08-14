package com.autoworkflow.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {
    List<Integration> findByUserId(UUID userId);
    Optional<Integration> findByUserIdAndProvider(UUID userId, String provider);
}
