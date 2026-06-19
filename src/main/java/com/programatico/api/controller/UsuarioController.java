package com.programatico.api.controller;

import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDto.Response> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        usuarioService.verificarProprioRecurso(id, userDetails.getUsername());
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioDto.UpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        usuarioService.verificarProprioRecurso(id, userDetails.getUsername());
        return ResponseEntity.ok(usuarioService.atualizar(id, request));
    }

    @PostMapping("/{id}/solicitar-exclusao")
    public ResponseEntity<UsuarioDto.MessageResponse> solicitarExclusao(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        usuarioService.verificarProprioRecurso(id, userDetails.getUsername());
        return ResponseEntity.ok(usuarioService.solicitarExclusaoConta(id));
    }

    @PostMapping("/{id}/confirmar-exclusao")
    public ResponseEntity<Void> confirmarExclusao(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioDto.ConfirmarExclusaoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        usuarioService.verificarProprioRecurso(id, userDetails.getUsername());
        usuarioService.confirmarExclusaoConta(id, request);
        return ResponseEntity.noContent().build();
    }
}
