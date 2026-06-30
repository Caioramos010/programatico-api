package com.programatico.api.service;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.SettingsDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private BackupCodeService backupCodeService;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private Usuario usuario;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .username("user")
                .email("user@test.com")
                .role(TipoUsuario.USER)
                .build();
        settings = UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(false)
                .disableAllNotifications(false)
                .build();
    }

    @Test
    void obterPreferenciasNotificacaoDeveCriarSettingsQuandoInexistentes() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsDto.NotificationPreferencesResponse response =
                userSettingsService.obterPreferenciasNotificacao("user");

        assertFalse(response.isDisableAllNotifications());
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void atualizarPreferenciasNotificacaoDeveDesativarTodasQuandoDisableAll() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsDto.NotificationPreferencesRequest request = SettingsDto.NotificationPreferencesRequest.builder()
                .disableAllNotifications(true)
                .build();

        SettingsDto.NotificationPreferencesResponse response =
                userSettingsService.atualizarPreferenciasNotificacao("user", request);

        assertTrue(response.isDisableAllNotifications());
        assertTrue(settings.getDisableUpdateNotifications());
        assertTrue(settings.getDisableEmailNotifications());
    }

    @Test
    void atualizarPreferenciasSegurancaDeveGerarBackupCodesAoAtivar2fa() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeService.gerarParaUsuario(usuario)).thenReturn(List.of("ABCD-2345"));
        when(backupCodeService.contarDisponiveis(usuario)).thenReturn(1);

        SettingsDto.SecurityPreferencesRequest request = SettingsDto.SecurityPreferencesRequest.builder()
                .twoFactorEnabled(true)
                .build();

        SettingsDto.SecurityPreferencesResponse response =
                userSettingsService.atualizarPreferenciasSeguranca("user", request);

        assertTrue(response.isTwoFactorEnabled());
        assertEquals(1, response.getBackupCodes().size());
        verify(backupCodeService).gerarParaUsuario(usuario);
    }

    @Test
    void atualizarPreferenciasSegurancaDeveInvalidarBackupCodesAoDesativar2fa() {
        settings.setTwoFactorEnabled(true);
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeService.contarDisponiveis(usuario)).thenReturn(0);

        userSettingsService.atualizarPreferenciasSeguranca("user",
                SettingsDto.SecurityPreferencesRequest.builder().twoFactorEnabled(false).build());

        verify(backupCodeService).invalidarTodos(usuario);
        verify(backupCodeService, never()).gerarParaUsuario(usuario);
    }

    @Test
    void podeNotificarDeveRespeitarPreferenciasPorTipo() {
        settings.setDisableSubscriptionNotifications(true);
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings));

        assertFalse(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.SUBSCRIPTION));
        assertTrue(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.MISSIONS));
    }

    @Test
    void obterPreferenciasDeveFalharQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userSettingsService.obterPreferenciasNotificacao("inexistente"));
    }
}
