package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findByIdAndUsuario(Long id, Usuario usuario);

    /** Sessão aberta (não encerrada) deste usuário no módulo — usada para retomar de onde parou. */
    Optional<PracticeSession> findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(
            Usuario usuario, Modulo modulo);

    /** Todas as sessões abertas do usuário — para marcar módulos "em andamento" na trilha. */
    List<PracticeSession> findByUsuarioAndEndedAtIsNull(Usuario usuario);

    long countByEndedAtIsNullAndStartedAtAfter(LocalDateTime startedAt);

    List<PracticeSession> findByUsuarioAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
            Usuario usuario,
            LocalDateTime startedAt
    );

    List<PracticeSession> findByUsuarioAndModulo_TrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
            Usuario usuario,
            Track track,
            LocalDateTime startedAt
    );

    @Modifying
    @Query("delete from PracticeSession ps where ps.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
