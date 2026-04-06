package com.programatico.api.controller;

import com.programatico.api.dto.TeoriaPaginaDto;
import com.programatico.api.service.AdminTeoriaPaginaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminTeoriaPaginaController {

    private final AdminTeoriaPaginaService adminTeoriaPaginaService;

    @GetMapping("/api/admin/modulos/{moduloId}/paginas")
    public ResponseEntity<List<TeoriaPaginaDto.Response>> listar(@PathVariable Long moduloId) {
        return ResponseEntity.ok(adminTeoriaPaginaService.listar(moduloId));
    }

    @PostMapping("/api/admin/modulos/{moduloId}/paginas")
    public ResponseEntity<TeoriaPaginaDto.Response> criar(
            @PathVariable Long moduloId,
            @Valid @RequestBody TeoriaPaginaDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminTeoriaPaginaService.criar(moduloId, request));
    }

    @PutMapping("/api/admin/paginas/{id}")
    public ResponseEntity<TeoriaPaginaDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TeoriaPaginaDto.Request request) {
        return ResponseEntity.ok(adminTeoriaPaginaService.atualizar(id, request));
    }

    @DeleteMapping("/api/admin/paginas/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminTeoriaPaginaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
