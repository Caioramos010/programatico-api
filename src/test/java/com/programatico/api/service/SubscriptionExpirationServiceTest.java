package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserSettingsService userSettingsService;

    @InjectMocks
    private SubscriptionExpirationService subscriptionExpirationService;

    @Test
    void processarAssinaturasExpiradasDeveRebaixarRootExpiradoENotificar() {
        Usuario expirado = usuarioRoot(1L, Instant.now().minus(1, ChronoUnit.DAYS));
        when(usuarioRepository.findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                eq(SubscriptionType.ROOT), any(Instant.class)))
                .thenReturn(List.of(expirado));
        when(userSettingsService.podeNotificar("user-1", UserSettingsService.NotificationKind.SUBSCRIPTION))
                .thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        int total = subscriptionExpirationService.processarAssinaturasExpiradas();

        assertEquals(1, total);
        assertEquals(SubscriptionType.FREE, expirado.getSubscriptionType());
        assertNull(expirado.getSubscriptionExpiresAt());
        verify(notificationService).criarNotificacaoSistema(
                expirado,
                SubscriptionExpirationService.TITULO_EXPIRACAO,
                SubscriptionExpirationService.MENSAGEM_EXPIRACAO,
                NotificationKind.SUBSCRIPTION
        );
    }

    @Test
    void processarAssinaturasExpiradasDeveRebaixarSemNotificarQuandoPreferenciaDesativada() {
        Usuario expirado = usuarioRoot(2L, Instant.now().minus(2, ChronoUnit.HOURS));
        when(usuarioRepository.findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                eq(SubscriptionType.ROOT), any(Instant.class)))
                .thenReturn(List.of(expirado));
        when(userSettingsService.podeNotificar("user-2", UserSettingsService.NotificationKind.SUBSCRIPTION))
                .thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        int total = subscriptionExpirationService.processarAssinaturasExpiradas();

        assertEquals(1, total);
        assertEquals(SubscriptionType.FREE, expirado.getSubscriptionType());
        verify(notificationService, never()).criarNotificacaoSistema(
                any(), any(), any(), any(NotificationKind.class));
    }

    @Test
    void processarAssinaturasExpiradasDeveRetornarZeroQuandoNenhumExpirado() {
        when(usuarioRepository.findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                eq(SubscriptionType.ROOT), any(Instant.class)))
                .thenReturn(List.of());

        int total = subscriptionExpirationService.processarAssinaturasExpiradas();

        assertEquals(0, total);
        verify(usuarioRepository, never()).save(any());
        verify(notificationService, never()).criarNotificacaoSistema(
                any(), any(), any(), any(NotificationKind.class));
    }

    @Test
    void processarAssinaturasExpiradasDeveProcessarVariosUsuarios() {
        Usuario u1 = usuarioRoot(10L, Instant.now().minus(5, ChronoUnit.DAYS));
        Usuario u2 = usuarioRoot(11L, Instant.now());
        when(usuarioRepository.findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                eq(SubscriptionType.ROOT), any(Instant.class)))
                .thenReturn(List.of(u1, u2));
        when(userSettingsService.podeNotificar(
                org.mockito.ArgumentMatchers.<String>any(),
                eq(UserSettingsService.NotificationKind.SUBSCRIPTION)))
                .thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        int total = subscriptionExpirationService.processarAssinaturasExpiradas();

        assertEquals(2, total);
        assertEquals(SubscriptionType.FREE, u1.getSubscriptionType());
        assertEquals(SubscriptionType.FREE, u2.getSubscriptionType());
        verify(notificationService).criarNotificacaoSistema(
                eq(u1), any(), any(), eq(NotificationKind.SUBSCRIPTION));
        verify(notificationService).criarNotificacaoSistema(
                eq(u2), any(), any(), eq(NotificationKind.SUBSCRIPTION));
    }

    private static Usuario usuarioRoot(Long id, Instant expiresAt) {
        return Usuario.builder()
                .id(id)
                .username("user-" + id)
                .email("user" + id + "@test.com")
                .senha("hash")
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(expiresAt)
                .build();
    }
}
