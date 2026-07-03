package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ExerciseRepositoryTest {

    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private TrackRepository trackRepository;

    private Modulo modulo;

    @BeforeEach
    void setUp() {
        Track track = trackRepository.save(Track.builder()
                .title("Trilha")
                .description("Desc")
                .displayOrder(1)
                .build());
        modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .build());

        exerciseRepository.save(exercise("E1", 5));
        exerciseRepository.save(exercise("E2", 10));
        exerciseRepository.save(exercise("E3", 5));
    }

    @Test
    void countByModulo() {
        assertEquals(3L, exerciseRepository.countByModulo(modulo));
    }

    @Test
    void sumXpByModulo() {
        assertEquals(20L, exerciseRepository.sumXpByModulo(modulo));
    }

    @Test
    void findByModuloAndXpReward() {
        List<Exercise> comCinco = exerciseRepository.findByModuloAndXpReward(modulo, 5);
        assertEquals(2, comCinco.size());
    }

    @Test
    void findByModuloOrderByIdAsc() {
        List<Exercise> ordenados = exerciseRepository.findByModuloOrderByIdAsc(modulo);
        assertEquals(3, ordenados.size());
        assertEquals("E1", ordenados.get(0).getStatement());
    }

    private Exercise exercise(String statement, int xp) {
        return Exercise.builder()
                .modulo(modulo)
                .statement(statement)
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(xp)
                .build();
    }
}
