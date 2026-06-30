package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.service.EmailService;
import com.programatico.api.service.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.verification-code.max-attempts=5",
        "app.verification-code.block-minutes=30"
})
class VerificationCodeBruteForceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;
    @MockitoBean private UserSettingsService userSettingsService;

    private static final String SENHA = "Senha@123";

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();
        org.mockito.Mockito.when(userSettingsService.isTwoFactorEnabled(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
    }

    @Test
    void loginDeveBloquearAposCincoCodigosInvalidos() throws Exception {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("brute-user")
                .email("brute@test.com")
                .senha(passwordEncoder.encode(SENHA))
                .idade(20)
                .ativo(true)
                .codigoVerificacaoLogin("123456")
                .dataExpiracaoCodigoLogin(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());

        String payload = """
                {
                  "emailOuUsername": "%s",
                  "senha": "%s",
                  "codigo": "000000"
                }
                """.formatted(usuario.getEmail(), SENHA);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login/confirmar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(
                            "Código inválido. Restam " + (4 - i) + " tentativa(s) antes do bloqueio temporário."));
        }

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Muitas tentativas inválidas. Tente novamente em 30 minutos."));

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());
    }
}
