package com.programatico.api.service;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @InjectMocks private NotificationService notificationService;

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
                .build();
    }

    @Test
    void listarPorUsuarioDeveRetornarNotificacoesOrdenadas() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario))
                .thenReturn(List.of(notification));

        var response = notificationService.listarPorUsuario("user");

        assertEquals(1, response.size());
        assertEquals("Módulo concluído", response.get(0).getTitle());
        assertEquals(false, response.get(0).getRead());
    }

    @Test
    void marcarComoLidaDeveAtualizarFlagRead() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(notificationRepository.findByIdAndUsuario(10L, usuario)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = notificationService.marcarComoLida(10L, "user");

        assertTrue(response.getRead());
        assertTrue(notification.getReadAt() != null);
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
