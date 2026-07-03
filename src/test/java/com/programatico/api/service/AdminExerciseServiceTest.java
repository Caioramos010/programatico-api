package com.programatico.api.service;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.ExerciseDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminExerciseServiceTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @InjectMocks private AdminExerciseService adminExerciseService;

    @Test
    void listarPorModuloDeveRetornarExercicios() {
        Modulo modulo = moduloBase();
        Exercise exercise = exerciseBase(modulo);
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(List.of(exercise));

        List<ExerciseDto.Response> response = adminExerciseService.listarPorModulo(1L);

        assertEquals(1, response.size());
        assertEquals("Enunciado", response.get(0).getStatement());
    }

    @Test
    void criarDevePersistirExercicio() {
        Modulo modulo = moduloBase();
        ExerciseDto.Request request = ExerciseDto.Request.builder()
                .statement("Novo")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(5)
                .build();
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> {
            Exercise e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        ExerciseDto.Response response = adminExerciseService.criar(1L, request);

        assertEquals("Novo", response.getStatement());
        verify(exerciseRepository).save(any(Exercise.class));
    }

    @Test
    void deletarDeveRemoverReferenciasDeSessao() {
        Modulo modulo = moduloBase();
        Exercise exercise = exerciseBase(modulo);
        exercise.setId(5L);
        when(exerciseRepository.findById(5L)).thenReturn(Optional.of(exercise));
        when(practiceSessionExerciseRepository.findByExercise(exercise)).thenReturn(List.of());

        adminExerciseService.deletar(5L);

        verify(exerciseRepository).deleteById(5L);
    }

    @Test
    void criarDeveFalharQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminExerciseService.criar(99L,
                ExerciseDto.Request.builder().statement("X").exerciseType(ExerciseType.MULTIPLE_CHOICE)
                        .exerciseData("{}").xpReward(1).build()));
    }

    private Modulo moduloBase() {
        Track track = Track.builder().id(1L).title("T").description("D").displayOrder(1).build();
        return Modulo.builder().id(1L).track(track).title("M").moduleType(ModuleType.ACTIVITY)
                .displayOrder(1).description("D").build();
    }

    private Exercise exerciseBase(Modulo modulo) {
        return Exercise.builder()
                .id(1L)
                .modulo(modulo)
                .statement("Enunciado")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(3)
                .build();
    }
}
