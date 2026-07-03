package com.programatico.api.service;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.dto.NotificationDto;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void listarPorUsuarioDeveRetornarNotificacoesMapeadas() {
        Usuario usuario = usuarioBase();
        Notification notification = Notification.builder()
                .id(1L)
                .usuario(usuario)
                .title("Nova trilha desbloqueada")
                .message("Voce desbloqueou mais uma trilha")
                .kind(NotificationKind.TRILHA)
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario)).thenReturn(List.of(notification));

        List<NotificationDto.Response> response = notificationService.listarPorUsuario("user");

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).getId());
        assertEquals("Nova trilha desbloqueada", response.get(0).getTitle());
        assertEquals(NotificationKind.TRILHA, response.get(0).getKind());
    }

    @Test
    void buscarPorIdDeveRetornarNotificacaoDoUsuario() {
        Usuario usuario = usuarioBase();
        Notification notification = Notification.builder()
                .id(10L)
                .usuario(usuario)
                .title("Missao concluida")
                .message("Voce completou a missao")
                .kind(NotificationKind.MISSAO)
                .read(true)
                .createdAt(Instant.now())
                .readAt(Instant.now())
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(10L, usuario)).thenReturn(Optional.of(notification));

        NotificationDto.Response response = notificationService.buscarPorId(10L, "user");

        assertEquals(10L, response.getId());
        assertTrue(response.getRead());
        assertEquals("Missao concluida", response.getTitle());
    }

    @Test
    void marcarComoLidaDeveAtualizarStatusEReadAt() {
        Usuario usuario = usuarioBase();
        Notification notification = Notification.builder()
                .id(2L)
                .usuario(usuario)
                .title("Exercicio concluido")
                .message("Parabens")
                .kind(NotificationKind.EXERCICIO)
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(2L, usuario)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationDto.Response response = notificationService.marcarComoLida(2L, "user");

        assertTrue(response.getRead());
        assertNotNull(response.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void excluirDeveRemoverNotificacaoDoUsuario() {
        Usuario usuario = usuarioBase();
        Notification notification = Notification.builder()
                .id(3L)
                .usuario(usuario)
                .title("Nova missao")
                .message("Missao disponivel")
                .kind(NotificationKind.MISSAO)
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(3L, usuario)).thenReturn(Optional.of(notification));

        notificationService.excluir(3L, "user");

        verify(notificationRepository).delete(notification);
    }

    private Usuario usuarioBase() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("user");
        usuario.setEmail("user@email.com");
        usuario.setSenha("hash");
        usuario.setAtivo(true);
        return usuario;
    }
}
