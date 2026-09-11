package com.autoworkflow.integration.oauth;

import com.autoworkflow.common.exception.IntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OAuthStateServiceTest {
    private OAuthStateRepository repository;
    private OAuthStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(OAuthStateRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        service = new OAuthStateService(repository);
    }

    @Test
    void createStoresOpaqueExpiringStateBoundToUserAndProvider() {
        UUID userId = UUID.randomUUID();
        String state = service.create(userId, "gmail");
        ArgumentCaptor<OAuthState> captor = ArgumentCaptor.forClass(OAuthState.class);
        verify(repository).save(captor.capture());

        OAuthState saved = captor.getValue();
        assertThat(state).isNotBlank().doesNotContain(userId.toString());
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getProvider()).isEqualTo("gmail");
        assertThat(saved.getStateHash()).isNotEqualTo(state);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void consumeRejectsProviderMismatchAndReplay() {
        UUID userId = UUID.randomUUID();
        OAuthState saved = OAuthState.builder().id(UUID.randomUUID()).stateHash("hash").userId(userId)
                .provider("gmail").expiresAt(Instant.now().plusSeconds(300)).build();
        when(repository.findByStateHash(any())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.consume("state", "google_sheets"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("does not match");

        assertThatThrownBy(() -> service.consume("state", "gmail"))
                .isInstanceOf(IntegrationException.class);
        assertThat(saved.getUsedAt()).isNotNull();
    }

    @Test
    void consumeRejectsExpiredState() {
        OAuthState saved = OAuthState.builder().stateHash("hash").userId(UUID.randomUUID())
                .provider("gmail").expiresAt(Instant.now().minusSeconds(1)).build();
        when(repository.findByStateHash(any())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.consume("state", "gmail"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("invalid or expired");
        verify(repository, never()).save(any());
    }
}
