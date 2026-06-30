package com.programatico.api.controller;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long notificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("notif-user")
                .email("notif@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        Notification notification = notificationRepository.save(Notification.builder()
                .usuario(usuario)
                .title("Exercícios concluídos")
                .message("Voce ganhou 10 XP")
                .kind(NotificationKind.EXERCICIO)
                .read(false)
                .build());
        notificationId = notification.getId();
        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void listarEMarcarComoLidaDeveFuncionar() throws Exception {
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Exercícios concluídos"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(patch("/api/notificacoes/" + notificationId + "/marcar-como-lida")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void marcarTodasComoLidasDeveRetornar204() throws Exception {
        mockMvc.perform(patch("/api/notificacoes/marcar-todas-como-lidas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].read").value(true));
    }
}
