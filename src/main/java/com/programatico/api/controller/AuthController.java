package com.programatico.api.controller;

import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<UsuarioDto.LoginResponse> login(@Valid @RequestBody UsuarioDto.LoginRequest request) {
        return ResponseEntity.ok(usuarioService.login(request));
    }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioDto.Response> registro(@Valid @RequestBody UsuarioDto.RegistroRequest request) {
        return ResponseEntity.status(201).body(usuarioService.registro(request));
    }

    @PostMapping("/ativar")
    public ResponseEntity<UsuarioDto.MessageResponse> ativar(@Valid @RequestBody UsuarioDto.AtivacaoRequest request) {
        return ResponseEntity.ok(usuarioService.ativar(request));
    }

    @PostMapping("/ativar/solicitar")
    public ResponseEntity<UsuarioDto.MessageResponse> solicitarAtivacao(@Valid @RequestBody UsuarioDto.SolicitarAtivacaoRequest request) {
        return ResponseEntity.ok(usuarioService.solicitarAtivacao(request));
    }

    @PostMapping("/redefinir-senha/solicitar")
    public ResponseEntity<UsuarioDto.MessageResponse> solicitarRedefinicao(@Valid @RequestBody UsuarioDto.SolicitarRedefinicaoSenhaRequest request) {
        return ResponseEntity.ok(usuarioService.solicitarRedefinicaoSenha(request));
    }

    @PostMapping("/redefinir-senha/nova")
    public ResponseEntity<UsuarioDto.MessageResponse> novaSenha(@Valid @RequestBody UsuarioDto.NovaSenhaRequest request) {
        return ResponseEntity.ok(usuarioService.redefinirSenha(request));
    }
}
