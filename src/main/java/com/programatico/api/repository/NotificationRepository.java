package com.programatico.api.repository;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUsuarioOrderByCreatedAtDesc(Usuario usuario);

    Optional<Notification> findByIdAndUsuario(Long id, Usuario usuario);

    long countByUsuarioAndReadFalse(Usuario usuario);

    @Modifying
    @Query("delete from Notification n where n.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
