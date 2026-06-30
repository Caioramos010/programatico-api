package com.programatico.api.controller;

import com.programatico.api.dto.SettingsDto;
import com.programatico.api.service.TotpSettingsService;
import com.programatico.api.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuracoes")
@RequiredArgsConstructor
public class SettingsController {

    private final UserSettingsService userSettingsService;
    private final TotpSettingsService totpSettingsService;

    @GetMapping("/notificacoes")
    public ResponseEntity<SettingsDto.NotificationPreferencesResponse> obterNotificacoes(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userSettingsService.obterPreferenciasNotificacao(userDetails.getUsername()));
    }

    @PutMapping("/notificacoes")
    public ResponseEntity<SettingsDto.NotificationPreferencesResponse> atualizarNotificacoes(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SettingsDto.NotificationPreferencesRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                userSettingsService.atualizarPreferenciasNotificacao(userDetails.getUsername(), request)
        );
    }

    @GetMapping("/seguranca")
    public ResponseEntity<SettingsDto.SecurityPreferencesResponse> obterSeguranca(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userSettingsService.obterPreferenciasSeguranca(userDetails.getUsername()));
    }

    @PutMapping("/seguranca")
    public ResponseEntity<SettingsDto.SecurityPreferencesResponse> atualizarSeguranca(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SettingsDto.SecurityPreferencesRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                userSettingsService.atualizarPreferenciasSeguranca(userDetails.getUsername(), request)
        );
    }

    @GetMapping("/totp")
    public ResponseEntity<SettingsDto.TotpStatusResponse> obterTotp(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(totpSettingsService.obterStatus(userDetails.getUsername()));
    }

    @PostMapping("/totp/setup")
    public ResponseEntity<SettingsDto.TotpSetupResponse> configurarTotp(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(totpSettingsService.iniciarSetup(userDetails.getUsername()));
    }

    @PostMapping("/totp/ativar")
    public ResponseEntity<SettingsDto.TotpStatusResponse> ativarTotp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SettingsDto.TotpCodeRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(totpSettingsService.ativar(userDetails.getUsername(), request.getCodigo()));
    }

    @PostMapping("/totp/desativar")
    public ResponseEntity<SettingsDto.TotpStatusResponse> desativarTotp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SettingsDto.TotpCodeRequest request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(totpSettingsService.desativar(userDetails.getUsername(), request.getCodigo()));
    }
}
