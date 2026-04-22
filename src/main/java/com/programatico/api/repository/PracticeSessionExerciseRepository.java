package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionExerciseRepository extends JpaRepository<PracticeSessionExercise, Long> {

    List<PracticeSessionExercise> findByPracticeSessionOrderByDisplayOrderAsc(PracticeSession session);

    Optional<PracticeSessionExercise> findByPracticeSessionAndExerciseId(PracticeSession session, Long exerciseId);

    List<PracticeSessionExercise> findByExercise(Exercise exercise);
}
