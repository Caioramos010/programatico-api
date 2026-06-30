package com.programatico.api.controller;

import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.service.TrustedDeviceService;
import com.programatico.api.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;
    private final TrustedDeviceService trustedDeviceService;

    @PostMapping("/login/iniciar")
    public ResponseEntity<UsuarioDto.LoginIniciarResponse> iniciarLogin(
            @Valid @RequestBody UsuarioDto.LoginRequest request,
            @CookieValue(name = TrustedDeviceService.COOKIE_NAME, required = false) String trustedDevice
    ) {
        return ResponseEntity.ok(usuarioService.iniciarLogin(request, trustedDevice));
    }

    @PostMapping("/login/confirmar")
    public ResponseEntity<UsuarioDto.LoginResponse> confirmarLogin(
            @Valid @RequestBody UsuarioDto.LoginConfirmarRequest request,
            HttpServletResponse response
    ) {
        UsuarioDto.LoginResponse login = usuarioService.confirmarLogin(request);
        if (Boolean.TRUE.equals(request.getLembrarDispositivo())) {
            trustedDeviceService.definirCookie(response, login.getUsuario().getId());
        }
        return ResponseEntity.ok(login);
    }

    @PostMapping("/login/reenviar")
    public ResponseEntity<UsuarioDto.LoginIniciarResponse> reenviarCodigoLogin(
            @Valid @RequestBody UsuarioDto.LoginRequest request,
            @CookieValue(name = TrustedDeviceService.COOKIE_NAME, required = false) String trustedDevice
    ) {
        return ResponseEntity.ok(usuarioService.iniciarLogin(request, trustedDevice));
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
