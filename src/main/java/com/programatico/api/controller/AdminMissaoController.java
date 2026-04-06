package com.programatico.api.controller;

import com.programatico.api.dto.MissaoDto;
import com.programatico.api.service.AdminMissaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/missoes")
@RequiredArgsConstructor
public class AdminMissaoController {

    private final AdminMissaoService adminMissaoService;

    @GetMapping
    public ResponseEntity<List<MissaoDto.Response>> listar() {
        return ResponseEntity.ok(adminMissaoService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<MissaoDto.Response> criar(@Valid @RequestBody MissaoDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminMissaoService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MissaoDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody MissaoDto.Request request) {
        return ResponseEntity.ok(adminMissaoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        adminMissaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
