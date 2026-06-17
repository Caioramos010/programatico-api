package com.programatico.api.repository;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUsuarioOrderByCreatedAtDesc(Usuario usuario);

    Optional<Notification> findByIdAndUsuario(Long id, Usuario usuario);
}
