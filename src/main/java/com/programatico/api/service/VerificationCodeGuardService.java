package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class VerificationCodeGuardService {

    private final UsuarioRepository usuarioRepository;

    @Value("${app.verification-code.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.verification-code.block-minutes:30}")
    private int blockMinutes;

    public void ensureNotBlocked(Usuario usuario, VerificationCodeContext context) {
        Instant blockedUntil = getBlockedUntil(usuario, context);
        if (blockedUntil == null) {
            return;
        }
        if (blockedUntil.isAfter(Instant.now())) {
            throw new BadRequestException(mensagemBloqueio(blockedUntil));
        }
        resetAttempts(usuario, context);
    }

    public void recordFailedAttempt(Usuario usuario, VerificationCodeContext context) {
        int attempts = getFailedAttempts(usuario, context) + 1;
        String mensagem;
        if (attempts >= maxAttempts) {
            Instant blockedUntil = Instant.now().plus(blockMinutes, ChronoUnit.MINUTES);
            setBlockedUntil(usuario, context, blockedUntil);
            setFailedAttempts(usuario, context, 0);
            mensagem = "Muitas tentativas inválidas. Tente novamente em " + blockMinutes + " minutos.";
        } else {
            setFailedAttempts(usuario, context, attempts);
            int restantes = maxAttempts - attempts;
            mensagem = "Código inválido. Restam " + restantes + " tentativa(s) antes do bloqueio temporário.";
        }
        usuarioRepository.save(usuario);
        throw new BadRequestException(mensagem);
    }

    public void resetAttempts(Usuario usuario, VerificationCodeContext context) {
        setFailedAttempts(usuario, context, 0);
        setBlockedUntil(usuario, context, null);
    }

    private static String mensagemBloqueio(Instant blockedUntil) {
        long minutos = Math.max(1, Duration.between(Instant.now(), blockedUntil).toMinutes());
        return "Muitas tentativas inválidas. Tente novamente em " + minutos + " minuto(s).";
    }

    private static int getFailedAttempts(Usuario usuario, VerificationCodeContext context) {
        Integer attempts = switch (context) {
            case LOGIN -> usuario.getLoginCodeFailedAttempts();
            case ACTIVATION -> usuario.getActivationCodeFailedAttempts();
            case PASSWORD_RESET -> usuario.getPasswordResetCodeFailedAttempts();
        };
        return attempts == null ? 0 : attempts;
    }

    private static void setFailedAttempts(Usuario usuario, VerificationCodeContext context, int attempts) {
        switch (context) {
            case LOGIN -> usuario.setLoginCodeFailedAttempts(attempts);
            case ACTIVATION -> usuario.setActivationCodeFailedAttempts(attempts);
            case PASSWORD_RESET -> usuario.setPasswordResetCodeFailedAttempts(attempts);
        }
    }

    private static Instant getBlockedUntil(Usuario usuario, VerificationCodeContext context) {
        return switch (context) {
            case LOGIN -> usuario.getLoginCodeBlockedUntil();
            case ACTIVATION -> usuario.getActivationCodeBlockedUntil();
            case PASSWORD_RESET -> usuario.getPasswordResetCodeBlockedUntil();
        };
    }

    private static void setBlockedUntil(Usuario usuario, VerificationCodeContext context, Instant blockedUntil) {
        switch (context) {
            case LOGIN -> usuario.setLoginCodeBlockedUntil(blockedUntil);
            case ACTIVATION -> usuario.setActivationCodeBlockedUntil(blockedUntil);
            case PASSWORD_RESET -> usuario.setPasswordResetCodeBlockedUntil(blockedUntil);
        }
    }
}
