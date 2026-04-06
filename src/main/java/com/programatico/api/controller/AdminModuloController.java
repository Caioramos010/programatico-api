package com.programatico.api.controller;

import com.programatico.api.dto.ModuloDto;
import com.programatico.api.service.AdminModuloService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminModuloController {

    private final AdminModuloService adminModuloService;

    @GetMapping("/api/admin/trilhas/{trilhaId}/modulos")
    public ResponseEntity<List<ModuloDto.Response>> listar(@PathVariable Long trilhaId) {
        return ResponseEntity.ok(adminModuloService.listarPorTrilha(trilhaId));
    }

    @PostMapping("/api/admin/trilhas/{trilhaId}/modulos")
    public ResponseEntity<ModuloDto.Response> criar(
            @PathVariable Long trilhaId,
            @Valid @RequestBody ModuloDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminModuloService.criar(trilhaId, request));
    }

    @PutMapping("/api/admin/modulos/{id}")
    public ResponseEntity<ModuloDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ModuloDto.Request request) {
        return ResponseEntity.ok(adminModuloService.atualizar(id, request));
    }

    @PutMapping("/api/admin/trilhas/{trilhaId}/modulos/reordenar")
    public ResponseEntity<Void> reordenar(
            @PathVariable Long trilhaId,
            @RequestBody Map<String, List<Long>> body) {
        adminModuloService.reordenar(trilhaId, body.get("ids"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/modulos/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminModuloService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
