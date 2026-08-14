package com.autoworkflow.integration;

import com.autoworkflow.common.dto.StringListConverter;
import com.autoworkflow.common.enums.IntegrationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "integrations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Integration {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String provider; // github | slack | openai | gmail | google_sheets | notion | discord

    @Column(name = "account_label", length = 150)
    private String accountLabel;

    @Column(name = "encrypted_access_token", columnDefinition = "text")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", columnDefinition = "text")
    private String encryptedRefreshToken;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text")
    private List<String> scopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IntegrationStatus status = IntegrationStatus.DISCONNECTED;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
