package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByModuloOrderByIdAsc(Modulo modulo);

    List<Exercise> findByModuloAndXpReward(Modulo modulo, Integer xpReward);

    long countByModulo(Modulo modulo);

    @Query("SELECT COALESCE(SUM(e.xpReward), 0) FROM Exercise e WHERE e.modulo = :modulo")
    long sumXpByModulo(Modulo modulo);
}
