package com.programatico.api.service;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.SettingsDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private Usuario usuario() {
        return Usuario.builder().id(1L).username("user").build();
    }

    private UserSettings settings(boolean all, boolean missions) {
        return UserSettings.builder()
                .usuario(usuario())
                .disableAllNotifications(all)
                .disableMissionNotifications(missions)
                .disableUpdateNotifications(false)
                .disableDaystreakNotifications(false)
                .disableSubscriptionNotifications(false)
                .disableEmailNotifications(false)
                .build();
    }

    // ── podeNotificar ─────────────────────────────────────────────────

    @Test
    void podeNotificarRetornaTrueQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("ninguem")).thenReturn(Optional.empty());

        assertTrue(userSettingsService.podeNotificar("ninguem", UserSettingsService.NotificationKind.MISSIONS));
    }

    @Test
    void podeNotificarRetornaFalseQuandoTodasDesativadas() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings(true, false)));

        assertFalse(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.UPDATES));
    }

    @Test
    void podeNotificarRespeitaPreferenciaEspecifica() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings(false, true)));

        assertFalse(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.MISSIONS));
        assertTrue(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.UPDATES));
    }

    @Test
    void podeNotificarPorIdDelegaPorUsername() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario()));
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings(false, false)));

        assertTrue(userSettingsService.podeNotificar(1L, UserSettingsService.NotificationKind.EMAIL));
    }

    @Test
    void podeNotificarPorIdRetornaTrueQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(userSettingsService.podeNotificar(99L, UserSettingsService.NotificationKind.EMAIL));
    }

    // ── atualizarPreferenciasNotificacao ──────────────────────────────

    @Test
    void atualizarComDisableAllDesativaTodasAsPreferencias() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings(false, false)));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SettingsDto.NotificationPreferencesRequest request =
                SettingsDto.NotificationPreferencesRequest.builder()
                        .disableAllNotifications(true)
                        .disableUpdateNotifications(false)
                        .disableDaystreakNotifications(false)
                        .disableMissionNotifications(false)
                        .disableSubscriptionNotifications(false)
                        .disableEmailNotifications(false)
                        .build();

        userSettingsService.atualizarPreferenciasNotificacao("user", request);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        UserSettings salvo = captor.getValue();
        assertTrue(salvo.getDisableAllNotifications());
        assertTrue(salvo.getDisableUpdateNotifications());
        assertTrue(salvo.getDisableMissionNotifications());
        assertTrue(salvo.getDisableEmailNotifications());
    }

    @Test
    void atualizarSemDisableAllAplicaPreferenciasIndividuais() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings(false, false)));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SettingsDto.NotificationPreferencesRequest request =
                SettingsDto.NotificationPreferencesRequest.builder()
                        .disableAllNotifications(false)
                        .disableUpdateNotifications(true)
                        .disableDaystreakNotifications(false)
                        .disableMissionNotifications(true)
                        .disableSubscriptionNotifications(false)
                        .disableEmailNotifications(false)
                        .build();

        SettingsDto.NotificationPreferencesResponse response =
                userSettingsService.atualizarPreferenciasNotificacao("user", request);

        assertTrue(response.isDisableUpdateNotifications());
        assertTrue(response.isDisableMissionNotifications());
        assertFalse(response.isDisableAllNotifications());
        assertFalse(response.isDisableEmailNotifications());
    }

    @Test
    void atualizarLancaQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("ninguem")).thenReturn(Optional.empty());

        SettingsDto.NotificationPreferencesRequest request =
                SettingsDto.NotificationPreferencesRequest.builder()
                        .disableAllNotifications(false)
                        .disableUpdateNotifications(false)
                        .disableDaystreakNotifications(false)
                        .disableMissionNotifications(false)
                        .disableSubscriptionNotifications(false)
                        .disableEmailNotifications(false)
                        .build();

        assertThrows(ResourceNotFoundException.class,
                () -> userSettingsService.atualizarPreferenciasNotificacao("ninguem", request));
    }

    // ── obterPreferenciasNotificacao ──────────────────────────────────

    @Test
    void obterCriaPreferenciasPadraoQuandoNaoExistem() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario()));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SettingsDto.NotificationPreferencesResponse response =
                userSettingsService.obterPreferenciasNotificacao("user");

        assertFalse(response.isDisableAllNotifications());
        verify(userSettingsRepository).save(any(UserSettings.class));
    }
}
