package com.programatico.api.service;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.dto.ExerciseDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminExerciseServiceTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private PracticeSessionExerciseRepository practiceSessionExerciseRepository;

    @InjectMocks
    private AdminExerciseService adminExerciseService;

    @Test
    void listarPorModuloDeveRetornarExerciciosDoModulo() {
        Modulo modulo = moduloBase();
        Exercise exercise = exerciseBase(modulo);

        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(List.of(exercise));

        List<ExerciseDto.Response> response = adminExerciseService.listarPorModulo(1L);

        assertEquals(1, response.size());
        assertEquals("Quanto é 1+1?", response.get(0).getStatement());
        assertEquals(ExerciseType.MULTIPLE_CHOICE, response.get(0).getExerciseType());
    }

    @Test
    void listarPorModuloDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminExerciseService.listarPorModulo(99L));
    }

    @Test
    void criarDevePersistirExercicioComDadosDaRequest() {
        Modulo modulo = moduloBase();
        ExerciseDto.Request request = ExerciseDto.Request.builder()
                .statement("Novo enunciado")
                .exerciseType(ExerciseType.DRAG_DROP)
                .exerciseData("{}")
                .xpReward(50)
                .tags("logica")
                .imageData(null)
                .build();
        Exercise salvo = exerciseBase(modulo);
        salvo.setStatement("Novo enunciado");
        salvo.setExerciseType(ExerciseType.DRAG_DROP);

        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(exerciseRepository.save(any(Exercise.class))).thenReturn(salvo);

        adminExerciseService.criar(1L, request);

        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseRepository).save(captor.capture());
        assertEquals("Novo enunciado", captor.getValue().getStatement());
        assertEquals(ExerciseType.DRAG_DROP, captor.getValue().getExerciseType());
        assertEquals(50, captor.getValue().getXpReward());
        assertEquals(modulo, captor.getValue().getModulo());
    }

    @Test
    void criarDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminExerciseService.criar(99L, ExerciseDto.Request.builder()
                        .statement("S").exerciseType(ExerciseType.PAIRS).exerciseData("{}").xpReward(10).build()));
    }

    @Test
    void atualizarDeveAlterarCamposDoExercicio() {
        Modulo modulo = moduloBase();
        Exercise exercise = exerciseBase(modulo);
        ExerciseDto.Request request = ExerciseDto.Request.builder()
                .statement("Atualizado")
                .exerciseType(ExerciseType.PAIRS)
                .exerciseData("{\"a\":1}")
                .xpReward(99)
                .tags("nova-tag")
                .imageData("img")
                .build();

        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(i -> i.getArgument(0));

        ExerciseDto.Response response = adminExerciseService.atualizar(1L, request);

        assertEquals("Atualizado", response.getStatement());
        assertEquals(ExerciseType.PAIRS, response.getExerciseType());
        assertEquals(99, response.getXpReward());
        assertEquals("nova-tag", response.getTags());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoExercicioNaoExiste() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminExerciseService.atualizar(99L, ExerciseDto.Request.builder()
                        .statement("X").exerciseType(ExerciseType.MULTIPLE_CHOICE).exerciseData("{}").xpReward(1).build()));
    }

    @Test
    void deletarDeveRemoverSessoesEDeletarExercicioQuandoExiste() {
        Modulo modulo = moduloBase();
        Exercise exercise = exerciseBase(modulo);
        PracticeSessionExercise pse = new PracticeSessionExercise();

        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        when(practiceSessionExerciseRepository.findByExercise(exercise)).thenReturn(List.of(pse));

        adminExerciseService.deletar(1L);

        verify(practiceSessionExerciseRepository).deleteAll(List.of(pse));
        verify(exerciseRepository).deleteById(1L);
    }

    @Test
    void deletarDeveLancarExcecaoQuandoExercicioNaoExiste() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminExerciseService.deletar(99L));
        verify(exerciseRepository, never()).deleteById(any());
    }

    private Modulo moduloBase() {
        return Modulo.builder().id(1L).title("Módulo Teste").displayOrder(1).build();
    }

    private Exercise exerciseBase(Modulo modulo) {
        return Exercise.builder()
                .id(1L)
                .modulo(modulo)
                .statement("Quanto é 1+1?")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(10)
                .tags("matematica")
                .build();
    }
}
