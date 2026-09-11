package com.autoworkflow.integration.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {
    Optional<OAuthState> findByStateHash(String stateHash);
}
