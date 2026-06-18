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
                        .build()));
    }
}
