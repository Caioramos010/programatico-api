package com.programatico.api.scheduler;

import com.programatico.api.service.SubscriptionExpirationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationSchedulerTest {

    @Mock private SubscriptionExpirationService subscriptionExpirationService;
    @InjectMocks private SubscriptionExpirationScheduler scheduler;

    @Test
    void executarDowngradeDiarioDeveDelegarAoService() {
        when(subscriptionExpirationService.processarAssinaturasExpiradas()).thenReturn(2);

        scheduler.executarDowngradeDiario();

        verify(subscriptionExpirationService).processarAssinaturasExpiradas();
    }
}
