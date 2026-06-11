package com.programatico.api.service;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.NotificationDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto.Response> listarPorUsuario(String username) {
        Usuario usuario = buscarUsuarioPorUsername(username);
        return notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario).stream()
                .map(NotificationDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public NotificationDto.Response marcarComoLida(Long id, String username) {
        Usuario usuario = buscarUsuarioPorUsername(username);
        Notification notification = buscarNotificacao(id, usuario);
        notification.setRead(true);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationDto.Response.fromEntity(notificationRepository.save(notification));
    }

    @Transactional
    public void marcarTodasComoLidas(String username) {
        Usuario usuario = buscarUsuarioPorUsername(username);
        List<Notification> notifications = notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario);

        Instant readAt = Instant.now();
        notifications.forEach(notification -> {
            notification.setRead(true);
            if (notification.getReadAt() == null) {
                notification.setReadAt(readAt);
            }
        });

        notificationRepository.saveAll(notifications);
        log.info("Notificações marcadas como lidas: userId={}, total={}", usuario.getId(), notifications.size());
    }

    private Usuario buscarUsuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));
    }

    private Notification buscarNotificacao(Long id, Usuario usuario) {
        return notificationRepository.findByIdAndUsuario(id, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação", id));
    }
}
