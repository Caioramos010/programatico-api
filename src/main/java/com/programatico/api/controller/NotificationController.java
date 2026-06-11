package com.programatico.api.controller;

import com.programatico.api.dto.NotificationDto;
import com.programatico.api.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto.Response>> listarPorUsuario(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.listarPorUsuario(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDto.Response> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.buscarPorId(id, userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<NotificationDto.Response> criar(
            @Valid @RequestBody NotificationDto.Request request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.criar(request, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody NotificationDto.UpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.atualizar(id, request, userDetails.getUsername()));
    }

    @PatchMapping("/{id}/marcar-como-lida")
    public ResponseEntity<NotificationDto.Response> marcarComoLida(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.marcarComoLida(id, userDetails.getUsername()));
    }

    @PatchMapping("/marcar-todas-como-lidas")
    public ResponseEntity<Void> marcarTodasComoLidas(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.marcarTodasComoLidas(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.deletar(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
