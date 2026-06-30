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

    // Esqueleto Práticas (Hyorran): inicia uma sessão de prática por modo
    // (erros | fixacao | cronometrado). responder/concluir abaixo são reaproveitados.
    @PostMapping("/pratica/{modo}/iniciar")
    public ResponseEntity<SessaoDto.InicioResponse> iniciarPratica(
            @PathVariable String modo,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sessaoAtividadeService.iniciarPratica(modo, userDetails.getUsername()));
    }

    // Root: pratica os erros do usuário em um assunto específico.
    @PostMapping("/pratica/erros-assunto/iniciar")
    public ResponseEntity<SessaoDto.InicioResponse> iniciarPraticaErrosPorAssunto(
            @RequestParam String assunto,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                sessaoAtividadeService.iniciarPraticaErrosPorAssunto(assunto, userDetails.getUsername()));
    }

    // Maestria: exercício de reforço (mesmo módulo, mesma tag) quando o usuário erra.
    @GetMapping("/sessoes/{sessaoId}/reforco")
    public ResponseEntity<SessaoDto.ExercicioSessao> reforco(
            @PathVariable Long sessaoId,
            @RequestParam Long exercicioId,
            @RequestParam(required = false) java.util.List<Long> excluir,
            @AuthenticationPrincipal UserDetails userDetails) {
        SessaoDto.ExercicioSessao reforco = sessaoAtividadeService.buscarReforco(
                sessaoId, exercicioId, excluir, userDetails.getUsername());
        return reforco != null ? ResponseEntity.ok(reforco) : ResponseEntity.noContent().build();
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
