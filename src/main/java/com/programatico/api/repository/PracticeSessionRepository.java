package com.programatico.api.repository;

import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findByIdAndUsuario(Long id, Usuario usuario);
}
