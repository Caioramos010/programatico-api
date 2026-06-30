package com.programatico.api.service;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TotpSettingsServiceTest {

    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Spy private TotpService totpService = new TotpService();
    @Mock private BackupCodeService backupCodeService;

    @InjectMocks
    private TotpSettingsService totpSettingsService;

    private final GoogleAuthenticator googleAuth = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setTimeStepSizeInMillis(30_000L)
                    .setWindowSize(1)
                    .build()
    );

    private Usuario usuario;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(totpService, "issuer", "Programático Teste");
        usuario = Usuario.builder()
                .id(1L)
                .username("user")
                .email("user@test.com")
                .role(TipoUsuario.USER)
                .build();
        settings = UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(true)
                .totpEnabled(false)
                .build();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userSettingsRepository.findByUsuarioId(1L)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void iniciarSetupDevePersistirSecretPendente() {
        var response = totpSettingsService.iniciarSetup("user");

        assertEquals(response.getSecret(), settings.getTotpPendingSecret());
        assertTrue(response.getQrCodeDataUrl().startsWith("data:image/png;base64,"));
    }

    @Test
    void iniciarSetupDeveFalharQuandoTotpJaAtivo() {
        settings.setTotpEnabled(true);

        assertThrows(BadRequestException.class, () -> totpSettingsService.iniciarSetup("user"));
    }

    @Test
    void ativarDeveConfirmarCodigoEGerarBackupCodes() {
        String secret = totpService.gerarSecret();
        settings.setTotpPendingSecret(secret);
        int codigo = googleAuth.getTotpPassword(secret);
        when(backupCodeService.gerarParaUsuario(usuario)).thenReturn(List.of("ABCD-2345"));

        var response = totpSettingsService.ativar("user", String.format("%06d", codigo));

        assertTrue(response.isTotpEnabled());
        assertTrue(settings.getTotpEnabled());
        assertEquals(secret, settings.getTotpSecret());
    }

    @Test
    void ativarDeveFalharQuandoCodigoInvalido() {
        settings.setTotpPendingSecret(totpService.gerarSecret());

        assertThrows(BadRequestException.class,
                () -> totpSettingsService.ativar("user", "000000"));
    }

    @Test
    void desativarDeveLimparTotpQuandoCodigoValido() {
        String secret = totpService.gerarSecret();
        settings.setTotpEnabled(true);
        settings.setTotpSecret(secret);
        int codigo = googleAuth.getTotpPassword(secret);

        var response = totpSettingsService.desativar("user", String.format("%06d", codigo));

        assertTrue(!response.isTotpEnabled());
        assertTrue(settings.getTotpSecret() == null);
    }
}
