package com.programatico.api.controller;

import com.programatico.api.dto.NotificationDto;
import com.programatico.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
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
}
