package com.programatico.api.controller;

import com.programatico.api.dto.AdminUsuarioDto;
import com.programatico.api.service.AdminUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    @GetMapping
    public ResponseEntity<List<AdminUsuarioDto.Response>> listar(
            @RequestParam(required = false) String busca) {
        return ResponseEntity.ok(adminUsuarioService.listarTodos(busca));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUsuarioDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AdminUsuarioDto.UpdateRequest request) {
        return ResponseEntity.ok(adminUsuarioService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminUsuarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
