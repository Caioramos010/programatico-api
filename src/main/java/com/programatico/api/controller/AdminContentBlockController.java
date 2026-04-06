package com.programatico.api.controller;

import com.programatico.api.dto.ContentBlockDto;
import com.programatico.api.service.AdminContentBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminContentBlockController {

    private final AdminContentBlockService adminContentBlockService;

    @GetMapping("/api/admin/paginas/{paginaId}/content-blocks")
    public ResponseEntity<List<ContentBlockDto.Response>> listarPorPagina(@PathVariable Long paginaId) {
        return ResponseEntity.ok(adminContentBlockService.listarPorPagina(paginaId));
    }

    @PostMapping("/api/admin/paginas/{paginaId}/content-blocks")
    public ResponseEntity<ContentBlockDto.Response> criarParaPagina(
            @PathVariable Long paginaId,
            @Valid @RequestBody ContentBlockDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminContentBlockService.criarParaPagina(paginaId, request));
    }

    @GetMapping("/api/admin/modulos/{moduloId}/content-blocks")
    public ResponseEntity<List<ContentBlockDto.Response>> listar(@PathVariable Long moduloId) {
        return ResponseEntity.ok(adminContentBlockService.listarPorModulo(moduloId));
    }

    @PostMapping("/api/admin/modulos/{moduloId}/content-blocks")
    public ResponseEntity<ContentBlockDto.Response> criar(
            @PathVariable Long moduloId,
            @Valid @RequestBody ContentBlockDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminContentBlockService.criar(moduloId, request));
    }

    @PutMapping("/api/admin/content-blocks/{id}")
    public ResponseEntity<ContentBlockDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ContentBlockDto.Request request) {
        return ResponseEntity.ok(adminContentBlockService.atualizar(id, request));
    }

    @DeleteMapping("/api/admin/content-blocks/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminContentBlockService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
