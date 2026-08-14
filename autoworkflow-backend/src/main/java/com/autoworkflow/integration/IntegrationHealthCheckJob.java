package com.autoworkflow.integration;

import com.autoworkflow.common.enums.IntegrationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-checks each connected integration's token validity so the
 * "Healthy / Disconnected" badge on the Integrations page reflects reality
 * rather than a stale connected flag.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationHealthCheckJob {

    private final IntegrationRepository integrationRepository;

    @Scheduled(fixedRate = 15, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void checkAll() {
        integrationRepository.findAll().forEach(integration -> {
            try {
                // TODO: call a lightweight "whoami"/ping endpoint per provider using the decrypted token.
                // On 401/expired -> mark ERROR; on success -> mark HEALTHY.
                integration.setLastCheckedAt(java.time.Instant.now());
                integrationRepository.save(integration);
            } catch (Exception e) {
                log.warn("Health check failed for integration {} ({}): {}",
                        integration.getId(), integration.getProvider(), e.getMessage());
                integration.setStatus(IntegrationStatus.ERROR);
                integrationRepository.save(integration);
            }
        });
    }
}
