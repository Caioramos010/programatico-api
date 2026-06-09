package com.programatico.api.repository;

import com.programatico.api.domain.ProcessedAbacateWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedAbacateWebhookRepository extends JpaRepository<ProcessedAbacateWebhook, String> {
}
