package com.programatico.api.controller;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.programatico.api.service.TotpService;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsIntegrationTest {

    private static final GoogleAuthenticator GOOGLE_AUTH = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setTimeStepSizeInMillis(30_000L)
                    .setWindowSize(1)
                    .build()
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TotpService totpService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("settings-user")
                .email("settings@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userSettingsRepository.save(UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(true)
                .totpEnabled(false)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void atualizarNotificacoesDevePersistirPreferencias() throws Exception {
        mockMvc.perform(put("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disableUpdateNotifications": false,
                                  "disableDaystreakNotifications": false,
                                  "disableMissionNotifications": false,
                                  "disableSubscriptionNotifications": true,
                                  "disableEmailNotifications": false,
                                  "disableAllNotifications": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableSubscriptionNotifications").value(true));

        mockMvc.perform(get("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.disableSubscriptionNotifications").value(true));
    }

    @Test
    void desativarTotpDeveFuncionar() throws Exception {
        String secret = totpService.gerarSecret();
        UserSettings settings = userSettingsRepository.findByUsuarioId(
                usuarioRepository.findByUsername("settings-user").orElseThrow().getId()).orElseThrow();
        settings.setTotpEnabled(true);
        settings.setTotpSecret(secret);
        userSettingsRepository.save(settings);

        int codigo = GOOGLE_AUTH.getTotpPassword(secret);

        mockMvc.perform(post("/api/configuracoes/totp/desativar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + String.format("%06d", codigo) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(false));
    }

    @Test
    void atualizarSegurancaDevePersistirPreferencias() throws Exception {
        mockMvc.perform(put("/api/configuracoes/seguranca")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));

        mockMvc.perform(get("/api/configuracoes/seguranca")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }
}
