package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionExerciseRepository extends JpaRepository<PracticeSessionExercise, Long> {

    List<PracticeSessionExercise> findByPracticeSessionOrderByDisplayOrderAsc(PracticeSession session);

    List<PracticeSessionExercise> findByPracticeSessionIn(List<PracticeSession> sessions);

    Optional<PracticeSessionExercise> findByPracticeSessionAndExerciseId(PracticeSession session, Long exerciseId);

    // Esqueleto Práticas (Hyorran): exercícios que o usuário errou, mais recentes primeiro.
    // Hyorran pode refinar (excluir já dominados, deduplicar mantendo o mais recente, etc.).
    @Query("select pse.exercise from PracticeSessionExercise pse " +
            "where pse.practiceSession.usuario = :usuario and pse.isCorrect = false " +
            "order by pse.id desc")
    List<Exercise> findExerciciosErradosDoUsuario(@Param("usuario") Usuario usuario);

    List<PracticeSessionExercise> findByExercise(Exercise exercise);

    @Modifying
    @Query("delete from PracticeSessionExercise pse where pse.practiceSession.usuario.id = :userId")
    void deleteByPracticeSessionUsuarioId(@Param("userId") Long userId);
}
