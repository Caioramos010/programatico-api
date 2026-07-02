package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TwoFactorLoginIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private Usuario usuario;
    private static final String SENHA = "Senha@123";

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(Usuario.builder()
                .username("2fa-user")
                .email("2fa@test.com")
                .senha(passwordEncoder.encode(SENHA))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userSettingsRepository.save(UserSettings.builder()
                .usuario(usuario)
                .build());
    }

    @Test
    void loginDeveExigirCodigoPorEmail() throws Exception {
        String json = """
                {
                  "emailOuUsername": "2fa@test.com",
                  "senha": "%s"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true))
                .andExpect(jsonPath("$.verificationMethod").value("EMAIL"));
    }

    @Test
    void fluxoLogin2faPontaAPontaDeveExigirCodigoEConfirmar() throws Exception {
        String iniciarJson = """
                {
                  "emailOuUsername": "2fa@test.com",
                  "senha": "%s"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(iniciarJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true))
                .andExpect(jsonPath("$.verificationMethod").value("EMAIL"));

        Usuario atualizado = usuarioRepository.findByEmail("2fa@test.com").orElseThrow();
        assertNotNull(atualizado.getCodigoVerificacaoLogin());

        String confirmarJson = """
                {
                  "emailOuUsername": "2fa@test.com",
                  "senha": "%s",
                  "codigo": "%s"
                }
                """.formatted(SENHA, atualizado.getCodigoVerificacaoLogin());

        MvcResult result = mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.username").value("2fa-user"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(body.get("token").asText());
    }

    @Test
    void confirmarLoginDeveFalharQuandoCodigoExpirado() throws Exception {
        usuario.setCodigoVerificacaoLogin("654321");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().minus(1, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);

        String confirmarJson = """
                {
                  "emailOuUsername": "2fa@test.com",
                  "senha": "%s",
                  "codigo": "654321"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Código expirado. Volte à tela de login e tente novamente."));
    }

    @Test
    void confirmarLoginDeveRejeitarCodigoInvalido() throws Exception {
        usuario.setCodigoVerificacaoLogin("111111");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().plus(1, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);

        String confirmarJson = """
                {
                  "emailOuUsername": "2fa@test.com",
                  "senha": "%s",
                  "codigo": "000000"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Código inválido. Restam 4 tentativa(s) antes do bloqueio temporário."));
    }
}
