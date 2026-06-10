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
    public List<NotificationDto.Response> listarPorUsuario(Long userId) {
        Usuario usuario = buscarUsuario(userId);
        return notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario).stream()
                .map(NotificationDto.Response::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationDto.Response buscarPorId(Long id) {
        return NotificationDto.Response.fromEntity(buscarNotificacao(id));
    }

    @Transactional
    public NotificationDto.Response criar(NotificationDto.Request request) {
        Usuario usuario = buscarUsuario(request.getUserId());

        Notification notification = Notification.builder()
                .usuario(usuario)
                .title(request.getTitle())
                .message(request.getMessage())
                .kind(request.getKind())
                .read(Boolean.TRUE.equals(request.getRead()))
                .readAt(Boolean.TRUE.equals(request.getRead()) ? Instant.now() : null)
                .build();

        Notification salva = notificationRepository.save(notification);
        log.info("Notificação criada: id={}, userId={}, kind={}", salva.getId(), usuario.getId(), salva.getKind());
        return NotificationDto.Response.fromEntity(salva);
    }

    @Transactional
    public NotificationDto.Response atualizar(Long id, NotificationDto.UpdateRequest request) {
        Notification notification = buscarNotificacao(id);

        if (request.getTitle() != null) {
            notification.setTitle(request.getTitle());
        }
        if (request.getMessage() != null) {
            notification.setMessage(request.getMessage());
        }
        if (request.getKind() != null) {
            notification.setKind(request.getKind());
        }
        if (request.getRead() != null) {
            notification.setRead(request.getRead());
            notification.setReadAt(Boolean.TRUE.equals(request.getRead()) ? Instant.now() : null);
        }

        Notification atualizada = notificationRepository.save(notification);
        return NotificationDto.Response.fromEntity(atualizada);
    }

    @Transactional
    public NotificationDto.Response marcarComoLida(Long id) {
        Notification notification = buscarNotificacao(id);
        notification.setRead(true);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationDto.Response.fromEntity(notificationRepository.save(notification));
    }

    @Transactional
    public void marcarTodasComoLidas(Long userId) {
        Usuario usuario = buscarUsuario(userId);
        List<Notification> notifications = notificationRepository.findByUsuarioOrderByCreatedAtDesc(usuario);

        Instant readAt = Instant.now();
        notifications.forEach(notification -> {
            notification.setRead(true);
            if (notification.getReadAt() == null) {
                notification.setReadAt(readAt);
            }
        });

        notificationRepository.saveAll(notifications);
        log.info("Notificações marcadas como lidas: userId={}, total={}", userId, notifications.size());
    }

    @Transactional
    public void deletar(Long id) {
        Notification notification = buscarNotificacao(id);
        notificationRepository.delete(notification);
        log.info("Notificação deletada: id={}", id);
    }

    private Usuario buscarUsuario(Long userId) {
        return usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", userId));
    }

    private Notification buscarNotificacao(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação", id));
    }
}
