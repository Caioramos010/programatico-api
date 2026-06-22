package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean
    private EmailService emailService;

    private String token;
    private Long usuarioId;

    @BeforeEach
    void setUp() {
        userSettingsRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("settings-user")
                .email("settings@email.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(22)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        usuarioId = usuario.getId();
        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void deveCriarDefaultAtualizarEPersistirPreferencias() throws Exception {
        // GET inicial cria as preferências default (tudo false)
        mockMvc.perform(get("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(false))
                .andExpect(jsonPath("$.disableEmailNotifications").value(false));

        assertTrue(userSettingsRepository.findByUsuarioId(usuarioId).isPresent());

        // PUT desativando todas as notificações
        String payload = """
                {"disableUpdateNotifications":false,"disableDaystreakNotifications":false,
                 "disableMissionNotifications":false,"disableSubscriptionNotifications":false,
                 "disableEmailNotifications":false,"disableAllNotifications":true}
                """;
        mockMvc.perform(put("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(true))
                // disableAll=true cascateia para todas as flags
                .andExpect(jsonPath("$.disableEmailNotifications").value(true))
                .andExpect(jsonPath("$.disableMissionNotifications").value(true));

        // GET de novo confirma persistência
        mockMvc.perform(get("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(true))
                .andExpect(jsonPath("$.disableUpdateNotifications").value(true));

        // verificação direta no repositório
        assertEquals(Boolean.TRUE,
                userSettingsRepository.findByUsuarioId(usuarioId).orElseThrow().getDisableAllNotifications());
        assertEquals(1, userSettingsRepository.count());
    }

    @Test
    void deveAtualizarSomenteUmaFlagQuandoDisableAllFalse() throws Exception {
        String payload = """
                {"disableUpdateNotifications":false,"disableDaystreakNotifications":false,
                 "disableMissionNotifications":true,"disableSubscriptionNotifications":false,
                 "disableEmailNotifications":false,"disableAllNotifications":false}
                """;
        mockMvc.perform(put("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(false))
                .andExpect(jsonPath("$.disableMissionNotifications").value(true))
                .andExpect(jsonPath("$.disableEmailNotifications").value(false));

        mockMvc.perform(get("/api/configuracoes/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableMissionNotifications").value(true))
                .andExpect(jsonPath("$.disableUpdateNotifications").value(false));
    }
}
