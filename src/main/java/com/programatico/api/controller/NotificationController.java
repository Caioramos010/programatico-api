package com.programatico.api.controller;

import com.programatico.api.dto.NotificationDto;
import com.programatico.api.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto.Response>> listarPorUsuario(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.listarPorUsuario(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDto.Response> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<NotificationDto.Response> criar(@Valid @RequestBody NotificationDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationDto.Response> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody NotificationDto.UpdateRequest request) {
        return ResponseEntity.ok(notificationService.atualizar(id, request));
    }

    @PatchMapping("/{id}/marcar-como-lida")
    public ResponseEntity<NotificationDto.Response> marcarComoLida(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marcarComoLida(id));
    }

    @PatchMapping("/usuarios/{userId}/marcar-todas-como-lidas")
    public ResponseEntity<Void> marcarTodasComoLidas(@PathVariable Long userId) {
        notificationService.marcarTodasComoLidas(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        notificationService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
