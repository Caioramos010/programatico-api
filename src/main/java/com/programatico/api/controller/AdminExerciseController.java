package com.programatico.api.controller;

import com.programatico.api.dto.ExerciseDto;
import com.programatico.api.service.AdminExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminExerciseController {

    private final AdminExerciseService adminExerciseService;

    @GetMapping("/api/admin/modulos/{moduloId}/exercises")
    public ResponseEntity<List<ExerciseDto.Response>> listar(@PathVariable Long moduloId) {
        return ResponseEntity.ok(adminExerciseService.listarPorModulo(moduloId));
    }

    @PostMapping("/api/admin/modulos/{moduloId}/exercises")
    public ResponseEntity<ExerciseDto.Response> criar(
            @PathVariable Long moduloId,
            @Valid @RequestBody ExerciseDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminExerciseService.criar(moduloId, request));
    }

    @PutMapping("/api/admin/exercises/{id}")
    public ResponseEntity<ExerciseDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ExerciseDto.Request request) {
        return ResponseEntity.ok(adminExerciseService.atualizar(id, request));
    }

    @DeleteMapping("/api/admin/exercises/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminExerciseService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
