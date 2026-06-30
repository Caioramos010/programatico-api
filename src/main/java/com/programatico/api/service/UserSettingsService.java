package com.programatico.api.service;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.SettingsDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    public enum NotificationKind {
        UPDATES,
        DAYSTREAK,
        MISSIONS,
        SUBSCRIPTION,
        EMAIL
    }

    private final UserSettingsRepository userSettingsRepository;
    private final UsuarioRepository usuarioRepository;
    private final BackupCodeService backupCodeService;

    @Transactional(readOnly = true)
    public SettingsDto.NotificationPreferencesResponse obterPreferenciasNotificacao(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return SettingsDto.NotificationPreferencesResponse.fromEntity(obterOuCriar(usuario));
    }

    @Transactional
    public SettingsDto.NotificationPreferencesResponse atualizarPreferenciasNotificacao(
            String username,
            SettingsDto.NotificationPreferencesRequest request
    ) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        UserSettings settings = obterOuCriar(usuario);

        boolean disableAll = Boolean.TRUE.equals(request.getDisableAllNotifications());
        settings.setDisableAllNotifications(disableAll);
        if (disableAll) {
            aplicarTodasDesativadas(settings, true);
        } else {
            settings.setDisableUpdateNotifications(Boolean.TRUE.equals(request.getDisableUpdateNotifications()));
            settings.setDisableDaystreakNotifications(Boolean.TRUE.equals(request.getDisableDaystreakNotifications()));
            settings.setDisableMissionNotifications(Boolean.TRUE.equals(request.getDisableMissionNotifications()));
            settings.setDisableSubscriptionNotifications(Boolean.TRUE.equals(request.getDisableSubscriptionNotifications()));
            settings.setDisableEmailNotifications(Boolean.TRUE.equals(request.getDisableEmailNotifications()));
        }

        return SettingsDto.NotificationPreferencesResponse.fromEntity(userSettingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public SettingsDto.SecurityPreferencesResponse obterPreferenciasSeguranca(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        UserSettings settings = obterOuCriar(usuario);
        return SettingsDto.SecurityPreferencesResponse.fromEntity(
                settings,
                null,
                backupCodeService.contarDisponiveis(usuario)
        );
    }

    @Transactional
    public SettingsDto.SecurityPreferencesResponse atualizarPreferenciasSeguranca(
            String username,
            SettingsDto.SecurityPreferencesRequest request
    ) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        UserSettings settings = obterOuCriar(usuario);
        boolean estavaAtivo = Boolean.TRUE.equals(settings.getTwoFactorEnabled());
        boolean ativar = Boolean.TRUE.equals(request.getTwoFactorEnabled());
        settings.setTwoFactorEnabled(ativar);
        userSettingsRepository.save(settings);

        List<String> backupCodes = null;
        if (ativar && !estavaAtivo) {
            backupCodes = backupCodeService.gerarParaUsuario(usuario);
        } else if (!ativar && estavaAtivo) {
            backupCodeService.invalidarTodos(usuario);
        }

        return SettingsDto.SecurityPreferencesResponse.fromEntity(
                settings,
                backupCodes,
                backupCodeService.contarDisponiveis(usuario)
        );
    }

    @Transactional(readOnly = true)
    public boolean isTwoFactorEnabled(Usuario usuario) {
        return Boolean.TRUE.equals(obterOuCriar(usuario).getTwoFactorEnabled());
    }

    @Transactional(readOnly = true)
    public boolean podeNotificar(String username, NotificationKind kind) {
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        if (usuario == null) {
            return true;
        }
        UserSettings settings = obterOuCriar(usuario);
        if (Boolean.TRUE.equals(settings.getDisableAllNotifications())) {
            return false;
        }
        return switch (kind) {
            case UPDATES -> !Boolean.TRUE.equals(settings.getDisableUpdateNotifications());
            case DAYSTREAK -> !Boolean.TRUE.equals(settings.getDisableDaystreakNotifications());
            case MISSIONS -> !Boolean.TRUE.equals(settings.getDisableMissionNotifications());
            case SUBSCRIPTION -> !Boolean.TRUE.equals(settings.getDisableSubscriptionNotifications());
            case EMAIL -> !Boolean.TRUE.equals(settings.getDisableEmailNotifications());
        };
    }

    @Transactional(readOnly = true)
    public boolean podeNotificar(Long userId, NotificationKind kind) {
        return usuarioRepository.findById(userId)
                .map(u -> podeNotificar(u.getUsername(), kind))
                .orElse(true);
    }

    private static void aplicarTodasDesativadas(UserSettings settings, boolean disabled) {
        settings.setDisableUpdateNotifications(disabled);
        settings.setDisableDaystreakNotifications(disabled);
        settings.setDisableMissionNotifications(disabled);
        settings.setDisableSubscriptionNotifications(disabled);
        settings.setDisableEmailNotifications(disabled);
    }

    private UserSettings obterOuCriar(Usuario usuario) {
        return userSettingsRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> userSettingsRepository.save(UserSettings.builder()
                        .usuario(usuario)
                        .disableUpdateNotifications(false)
                        .disableDaystreakNotifications(false)
                        .disableMissionNotifications(false)
                        .disableSubscriptionNotifications(false)
                        .disableEmailNotifications(false)
                        .disableAllNotifications(false)
                        .twoFactorEnabled(true)
                        .totpEnabled(false)
                        .build()));
    }
}
