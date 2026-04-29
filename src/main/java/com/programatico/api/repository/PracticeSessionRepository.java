package com.programatico.api.repository;

import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findByIdAndUsuario(Long id, Usuario usuario);

    @Modifying
    @Query("delete from PracticeSession ps where ps.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
