package com.programatico.api.service;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.SettingsDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TotpSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UsuarioRepository usuarioRepository;
    private final TotpService totpService;
    private final BackupCodeService backupCodeService;

    @Transactional(readOnly = true)
    public SettingsDto.TotpStatusResponse obterStatus(String username) {
        Usuario usuario = buscarUsuario(username);
        UserSettings settings = obterSettings(usuario);
        return SettingsDto.TotpStatusResponse.builder()
                .totpEnabled(Boolean.TRUE.equals(settings.getTotpEnabled()))
                .setupPendente(StringUtils.hasText(settings.getTotpPendingSecret()))
                .build();
    }

    @Transactional
    public SettingsDto.TotpSetupResponse iniciarSetup(String username) {
        Usuario usuario = buscarUsuario(username);
        UserSettings settings = obterSettings(usuario);
        if (Boolean.TRUE.equals(settings.getTotpEnabled())) {
            throw new BadRequestException("O autenticador já está ativo. Desative antes de configurar novamente.");
        }
        String secret = totpService.gerarSecret();
        settings.setTotpPendingSecret(secret);
        userSettingsRepository.save(settings);
        return SettingsDto.TotpSetupResponse.builder()
                .secret(secret)
                .otpauthUrl(totpService.montarOtpAuthUrl(usuario.getEmail(), secret))
                .qrCodeDataUrl(totpService.gerarQrCodeDataUri(usuario.getEmail(), secret))
                .build();
    }

    @Transactional
    public SettingsDto.TotpActivationResponse ativar(String username, String codigo) {
        Usuario usuario = buscarUsuario(username);
        UserSettings settings = obterSettings(usuario);
        if (!StringUtils.hasText(settings.getTotpPendingSecret())) {
            throw new BadRequestException("Inicie a configuração do autenticador antes de ativar.");
        }
        if (!totpService.codigoValido(settings.getTotpPendingSecret(), codigo)) {
            throw new BadRequestException("Código do autenticador inválido. Verifique o app e tente novamente.");
        }
        settings.setTotpSecret(settings.getTotpPendingSecret());
        settings.setTotpPendingSecret(null);
        settings.setTotpEnabled(true);
        settings.setTwoFactorEnabled(true);
        userSettingsRepository.save(settings);
        List<String> backupCodes = backupCodeService.gerarParaUsuario(usuario);
        SettingsDto.TotpStatusResponse status = obterStatus(username);
        return SettingsDto.TotpActivationResponse.builder()
                .totpEnabled(status.isTotpEnabled())
                .setupPendente(status.isSetupPendente())
                .backupCodes(backupCodes)
                .build();
    }

    @Transactional
    public SettingsDto.TotpStatusResponse desativar(String username, String codigo) {
        Usuario usuario = buscarUsuario(username);
        UserSettings settings = obterSettings(usuario);
        if (!Boolean.TRUE.equals(settings.getTotpEnabled()) || !StringUtils.hasText(settings.getTotpSecret())) {
            throw new BadRequestException("O autenticador não está ativo.");
        }
        if (!totpService.codigoValido(settings.getTotpSecret(), codigo)) {
            throw new BadRequestException("Código do autenticador inválido.");
        }
        settings.setTotpEnabled(false);
        settings.setTotpSecret(null);
        settings.setTotpPendingSecret(null);
        userSettingsRepository.save(settings);
        return obterStatus(username);
    }

    @Transactional(readOnly = true)
    public boolean isTotpAtivo(Usuario usuario) {
        UserSettings settings = obterSettings(usuario);
        return Boolean.TRUE.equals(settings.getTotpEnabled()) && StringUtils.hasText(settings.getTotpSecret());
    }

    @Transactional(readOnly = true)
    public boolean validarCodigoLogin(Usuario usuario, String codigo) {
        UserSettings settings = obterSettings(usuario);
        if (!Boolean.TRUE.equals(settings.getTotpEnabled()) || !StringUtils.hasText(settings.getTotpSecret())) {
            return false;
        }
        return totpService.codigoValido(settings.getTotpSecret(), codigo);
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    private UserSettings obterSettings(Usuario usuario) {
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
