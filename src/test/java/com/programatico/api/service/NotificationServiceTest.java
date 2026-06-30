package com.programatico.api.service;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.NotificationDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Usuario usuario;
    private Notification notification;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .username("user")
                .email("user@test.com")
                .role(TipoUsuario.USER)
                .build();
        notification = Notification.builder()
                .id(10L)
                .usuario(usuario)
                .title("Módulo concluído")
                .message("Parabéns!")
                .kind(NotificationKind.TRILHA)
                .read(false)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void listarPorUsuarioDeveRetornarNotificacoesMapeadas() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario))
                .thenReturn(List.of(notification));

        List<NotificationDto.Response> response = notificationService.listarPorUsuario("user");

        assertEquals(1, response.size());
        assertEquals(10L, response.get(0).getId());
        assertEquals("Módulo concluído", response.get(0).getTitle());
        assertEquals(NotificationKind.TRILHA, response.get(0).getKind());
        assertEquals(false, response.get(0).getRead());
    }

    @Test
    void buscarPorIdDeveRetornarNotificacaoDoUsuario() {
        Notification readNotification = Notification.builder()
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
        when(notificationRepository.findByIdAndUsuario(10L, usuario)).thenReturn(Optional.of(readNotification));

        NotificationDto.Response response = notificationService.buscarPorId(10L, "user");

        assertEquals(10L, response.getId());
        assertTrue(response.getRead());
        assertEquals("Missao concluida", response.getTitle());
    }

    @Test
    void marcarComoLidaDeveAtualizarStatusEReadAt() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(10L, usuario)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationDto.Response response = notificationService.marcarComoLida(10L, "user");

        assertTrue(response.getRead());
        assertNotNull(response.getReadAt());
        assertTrue(notification.getReadAt() != null);
        verify(notificationRepository).save(notification);
    }

    @Test
    void marcarTodasComoLidasDevePersistirLista() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario))
                .thenReturn(List.of(notification));

        notificationService.marcarTodasComoLidas("user");

        assertTrue(notification.getRead());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void excluirDeveRemoverNotificacaoDoUsuario() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(10L, usuario)).thenReturn(Optional.of(notification));

        notificationService.excluir(10L, "user");

        verify(notificationRepository).delete(notification);
    }

    @Test
    void criarNotificacaoSistemaDevePersistir() {
        notificationService.criarNotificacaoSistema(usuario, "Título", "Msg", NotificationKind.EXERCICIO);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void marcarComoLidaDeveFalharQuandoNotificacaoNaoPertenceAoUsuario() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(99L, usuario)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.marcarComoLida(99L, "user"));
    }
}
