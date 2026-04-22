package com.programatico.api.controller;

import com.programatico.api.dto.SessaoDto;
import com.programatico.api.service.SessaoAtividadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aprender")
@RequiredArgsConstructor
public class SessaoAtividadeController {

    private final SessaoAtividadeService sessaoAtividadeService;

    @PostMapping("/modulos/{moduloId}/iniciar")
    public ResponseEntity<SessaoDto.InicioResponse> iniciarSessao(
            @PathVariable Long moduloId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sessaoAtividadeService.iniciarSessao(moduloId, userDetails.getUsername()));
    }

    @PostMapping("/sessoes/{sessaoId}/responder")
    public ResponseEntity<SessaoDto.RespostaResponse> responder(
            @PathVariable Long sessaoId,
            @Valid @RequestBody SessaoDto.RespostaRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sessaoAtividadeService.responder(sessaoId, request, userDetails.getUsername()));
    }

    @PostMapping("/sessoes/{sessaoId}/concluir")
    public ResponseEntity<SessaoDto.ConclusaoResponse> concluir(
            @PathVariable Long sessaoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sessaoAtividadeService.concluir(sessaoId, userDetails.getUsername()));
    }
}
