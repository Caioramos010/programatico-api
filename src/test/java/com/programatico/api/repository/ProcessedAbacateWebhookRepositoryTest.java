package com.programatico.api.repository;

import com.programatico.api.domain.ProcessedAbacateWebhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ProcessedAbacateWebhookRepositoryTest {

    @Autowired private ProcessedAbacateWebhookRepository repository;

    @Test
    void existsById() {
        String eventId = "evt-123";
        assertFalse(repository.existsById(eventId));

        repository.save(ProcessedAbacateWebhook.builder()
                .eventId(eventId)
                .processedAt(java.time.Instant.now())
                .build());

        assertTrue(repository.existsById(eventId));
    }
}
