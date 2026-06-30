package com.programatico.api.controller;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.programatico.api.service.TotpService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TotpLoginIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private static final String SENHA = "Senha@123";
    private static final GoogleAuthenticator GOOGLE_AUTH = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setTimeStepSizeInMillis(30_000L)
                    .setWindowSize(1)
                    .build()
    );

    private String totpSecret;

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();

        totpSecret = totpService.gerarSecret();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("totp-user")
                .email("totp@test.com")
                .senha(passwordEncoder.encode(SENHA))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userSettingsRepository.save(UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(true)
                .totpEnabled(true)
                .totpSecret(totpSecret)
                .build());
    }

    @Test
    void loginComTotpNaoDeveEnviarEmail() throws Exception {
        String json = """
                {
                  "emailOuUsername": "totp@test.com",
                  "senha": "%s"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true))
                .andExpect(jsonPath("$.verificationMethod").value("TOTP"));

        verify(emailService, never()).enviarCodigoVerificacaoLogin(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loginComTotpValidoDeveRetornarToken() throws Exception {
        int codigo = GOOGLE_AUTH.getTotpPassword(totpSecret);

        String confirmar = """
                {
                  "emailOuUsername": "totp@test.com",
                  "senha": "%s",
                  "codigo": "%06d"
                }
                """.formatted(SENHA, codigo);

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("totp-user"));
    }

    @Test
    void setupEAtivacaoTotpDeveFuncionar() throws Exception {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("setup-totp")
                .email("setup-totp@test.com")
                .senha(passwordEncoder.encode(SENHA))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userSettingsRepository.save(UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(true)
                .totpEnabled(false)
                .build());

        String token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());

        mockMvc.perform(post("/api/configuracoes/totp/setup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.qrCodeDataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        UserSettings settings = userSettingsRepository.findByUsuarioId(usuario.getId()).orElseThrow();
        String pendingSecret = settings.getTotpPendingSecret();
        int codigo = GOOGLE_AUTH.getTotpPassword(pendingSecret);

        mockMvc.perform(post("/api/configuracoes/totp/ativar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + String.format("%06d", codigo) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true))
                .andExpect(jsonPath("$.backupCodes").isArray())
                .andExpect(jsonPath("$.backupCodes.length()").value(10));
    }
}
