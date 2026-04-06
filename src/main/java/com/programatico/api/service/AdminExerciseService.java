package com.programatico.api.service;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.dto.ExerciseDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminExerciseService {

    private static final Logger log = LoggerFactory.getLogger(AdminExerciseService.class);

    private final ExerciseRepository exerciseRepository;
    private final ModuloRepository moduloRepository;

    @Transactional(readOnly = true)
    public List<ExerciseDto.Response> listarPorModulo(Long moduloId) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        return exerciseRepository.findByModuloOrderByIdAsc(modulo).stream()
                .map(ExerciseDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public ExerciseDto.Response criar(Long moduloId, ExerciseDto.Request request) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        Exercise exercise = Exercise.builder()
                .modulo(modulo)
                .statement(request.getStatement())
                .exerciseType(request.getExerciseType())
                .exerciseData(request.getExerciseData())
                .xpReward(request.getXpReward())
                .tags(request.getTags())
                .imageData(request.getImageData())
                .build();
        Exercise salvo = exerciseRepository.save(exercise);
        log.info("Exercício criado: id={}, type={}", salvo.getId(), salvo.getExerciseType());
        return ExerciseDto.Response.fromEntity(salvo);
    }

    @Transactional
    public ExerciseDto.Response atualizar(Long id, ExerciseDto.Request request) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercício", id));
        exercise.setStatement(request.getStatement());
        exercise.setExerciseType(request.getExerciseType());
        exercise.setExerciseData(request.getExerciseData());
        exercise.setXpReward(request.getXpReward());
        exercise.setTags(request.getTags());
        exercise.setImageData(request.getImageData());
        return ExerciseDto.Response.fromEntity(exerciseRepository.save(exercise));
    }

    @Transactional
    public void deletar(Long id) {
        if (!exerciseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Exercício", id);
        }
        exerciseRepository.deleteById(id);
        log.info("Exercício deletado: id={}", id);
    }
}
