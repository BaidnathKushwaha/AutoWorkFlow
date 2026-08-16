package com.autoworkflow.user;

import com.autoworkflow.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Nullable — Google-only users have no password */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Google subject ID (sub claim) — used to link Google account */
    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    /** Profile picture URL from Google */
    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "api_key_encrypted", columnDefinition = "text")
    private String apiKeyEncrypted;

    @Column(name = "api_key_last_four", length = 4)
    private String apiKeyLastFour;

    /**
     * Default AI execution mode for this user.
     *
     * Supported values:
     * auto
     * openrouter
     * gemini
     * openai
     */
    @Column(name = "ai_provider", nullable = false, length = 30)
    @Builder.Default
    private String aiProvider = "auto";

    /**
     * Default AI model for this user.
     *
     * Null when aiProvider is auto because AUTO mode lets
     * AiProviderRouter select the provider and its configured model.
     */
    @Column(name = "ai_model", length = 150)
    private String aiModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "ai_requests_count", nullable = false)
    @Builder.Default
    private Long aiRequestsCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}