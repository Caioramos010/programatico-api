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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private Long unreadNotificationId;
    private Long readNotificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("notification-user")
                .email("notification@email.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        Notification unreadNotification = notificationRepository.save(Notification.builder()
                .usuario(usuario)
                .title("Nova trilha desbloqueada")
                .message("Voce desbloqueou mais uma trilha")
                .kind(NotificationKind.TRILHA)
                .read(false)
                .createdAt(Instant.now().minusSeconds(60))
                .build());
        unreadNotificationId = unreadNotification.getId();

        Notification readNotification = notificationRepository.save(Notification.builder()
                .usuario(usuario)
                .title("Exercicio concluido")
                .message("Parabens, voce completou o exercicio")
                .kind(NotificationKind.EXERCICIO)
                .read(true)
                .createdAt(Instant.now().minusSeconds(120))
                .readAt(Instant.now().minusSeconds(30))
                .build());
        readNotificationId = readNotification.getId();

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void listarPorUsuarioDeveRetornarNotificacoesDoUsuario() throws Exception {
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].message").exists());
    }

    @Test
    void buscarPorIdDeveRetornarNotificacaoDoUsuario() throws Exception {
        mockMvc.perform(get("/api/notificacoes/{id}", unreadNotificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unreadNotificationId))
                .andExpect(jsonPath("$.title").value("Nova trilha desbloqueada"))
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    void marcarComoLidaDeveAtualizarNotificacao() throws Exception {
        mockMvc.perform(patch("/api/notificacoes/{id}/marcar-como-lida", unreadNotificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unreadNotificationId))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        Notification notification = notificationRepository.findById(unreadNotificationId).orElseThrow();
        assertTrue(notification.getRead());
    }

    @Test
    void marcarTodasComoLidasDeveAtualizarTodasAsNotificacoes() throws Exception {
        mockMvc.perform(patch("/api/notificacoes/marcar-todas-como-lidas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().allMatch(notification -> Boolean.TRUE.equals(notification.getRead())));
    }

    @Test
    void excluirDeveRemoverNotificacaoDoUsuario() throws Exception {
        mockMvc.perform(delete("/api/notificacoes/{id}", readNotificationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(notificationRepository.findById(readNotificationId).isEmpty());
        assertEquals(1, notificationRepository.count());
    }

    @Test
    void deveExigirAutenticacaoParaListarNotificacoes() throws Exception {
        mockMvc.perform(get("/api/notificacoes"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }
}
