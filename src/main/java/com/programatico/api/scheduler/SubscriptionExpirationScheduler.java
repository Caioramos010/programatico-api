package com.programatico.api.scheduler;

import com.programatico.api.service.SubscriptionExpirationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationScheduler.class);

    private final SubscriptionExpirationService subscriptionExpirationService;

    /** Executa diariamente às 03:00 (horário do servidor). */
    @Scheduled(cron = "${app.subscription-expiration.cron:0 0 3 * * *}")
    public void executarDowngradeDiario() {
        try {
            int total = subscriptionExpirationService.processarAssinaturasExpiradas();
            log.debug("Job de expiração ROOT concluído: {} usuário(s) rebaixado(s)", total);
        } catch (Exception e) {
            log.error("Falha no job diário de expiração de assinatura ROOT", e);
        }
    }
}
