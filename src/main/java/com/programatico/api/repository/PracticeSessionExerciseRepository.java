package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionExerciseRepository extends JpaRepository<PracticeSessionExercise, Long> {

    List<PracticeSessionExercise> findByPracticeSessionOrderByDisplayOrderAsc(PracticeSession session);

    Optional<PracticeSessionExercise> findByPracticeSessionAndExerciseId(PracticeSession session, Long exerciseId);

    List<PracticeSessionExercise> findByExercise(Exercise exercise);

    @Modifying
    @Query("delete from PracticeSessionExercise pse where pse.practiceSession.usuario.id = :userId")
    void deleteByPracticeSessionUsuarioId(@Param("userId") Long userId);
}
