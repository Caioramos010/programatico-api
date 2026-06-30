package com.programatico.api.controller;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.service.EmailService;
import com.programatico.api.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RememberDeviceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;

    private static final String SENHA = "Senha@123";

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("remember-user")
                .email("remember@test.com")
                .senha(passwordEncoder.encode(SENHA))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userSettingsRepository.save(UserSettings.builder()
                .usuario(usuario)
                .twoFactorEnabled(true)
                .build());
    }

    @Test
    void dispositivoConfiavelDevePular2faNoProximoLogin() throws Exception {
        Usuario usuario = usuarioRepository.findByEmail("remember@test.com").orElseThrow();
        usuario.setCodigoVerificacaoLogin("123456");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().plus(1, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);

        String confirmar = """
                {
                  "emailOuUsername": "remember@test.com",
                  "senha": "%s",
                  "codigo": "123456",
                  "lembrarDispositivo": true
                }
                """.formatted(SENHA);

        MvcResult confirmResult = mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmar))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(TrustedDeviceService.COOKIE_NAME))
                .andReturn();

        Cookie deviceCookie = confirmResult.getResponse().getCookie(TrustedDeviceService.COOKIE_NAME);

        String iniciar = """
                {
                  "emailOuUsername": "remember@test.com",
                  "senha": "%s"
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(iniciar)
                        .cookie(deviceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());

        verify(emailService, never()).enviarCodigoVerificacaoLogin(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
