package com.programatico.api.controller;

import com.programatico.api.dto.TrilhaDto;
import com.programatico.api.service.AdminTrilhaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trilhas")
@RequiredArgsConstructor
public class AdminTrilhaController {

    private final AdminTrilhaService adminTrilhaService;

    @GetMapping
    public ResponseEntity<List<TrilhaDto.Response>> listar() {
        return ResponseEntity.ok(adminTrilhaService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<TrilhaDto.Response> criar(@Valid @RequestBody TrilhaDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminTrilhaService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrilhaDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TrilhaDto.Request request) {
        return ResponseEntity.ok(adminTrilhaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminTrilhaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
