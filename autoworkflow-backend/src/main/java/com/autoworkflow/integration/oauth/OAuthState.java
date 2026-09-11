package com.autoworkflow.integration.oauth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_states", indexes = {
        @Index(name = "idx_oauth_states_hash", columnList = "state_hash", unique = true),
        @Index(name = "idx_oauth_states_expires", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthState {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "state_hash", nullable = false, length = 64, unique = true)
    private String stateHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
