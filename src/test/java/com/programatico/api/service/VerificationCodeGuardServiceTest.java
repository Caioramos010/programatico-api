package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VerificationCodeGuardServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private VerificationCodeGuardService guardService;

    @BeforeEach
    void configurarLimites() {
        ReflectionTestUtils.setField(guardService, "maxAttempts", 5);
        ReflectionTestUtils.setField(guardService, "blockMinutes", 30);
    }

    @Test
    void deveBloquearAposQuintaTentativaInvalida() {
        Usuario usuario = usuarioBase();
        usuario.setLoginCodeFailedAttempts(4);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> guardService.recordFailedAttempt(usuario, VerificationCodeContext.LOGIN));

        assertEquals("Muitas tentativas inválidas. Tente novamente em 30 minutos.", ex.getMessage());
        assertEquals(0, usuario.getLoginCodeFailedAttempts());
        assertEquals(true, usuario.getLoginCodeBlockedUntil().isAfter(Instant.now()));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveInformarTentativasRestantesAntesDoBloqueio() {
        Usuario usuario = usuarioBase();
        usuario.setLoginCodeFailedAttempts(2);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> guardService.recordFailedAttempt(usuario, VerificationCodeContext.LOGIN));

        assertEquals("Código inválido. Restam 2 tentativa(s) antes do bloqueio temporário.", ex.getMessage());
        assertEquals(3, usuario.getLoginCodeFailedAttempts());
    }

    @Test
    void deveRejeitarQuandoAindaBloqueado() {
        Usuario usuario = usuarioBase();
        usuario.setLoginCodeBlockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThrows(BadRequestException.class,
                () -> guardService.ensureNotBlocked(usuario, VerificationCodeContext.LOGIN));
    }

    @Test
    void deveLimparBloqueioExpirado() {
        Usuario usuario = usuarioBase();
        usuario.setLoginCodeFailedAttempts(3);
        usuario.setLoginCodeBlockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));

        guardService.ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);

        assertEquals(0, usuario.getLoginCodeFailedAttempts());
        assertNull(usuario.getLoginCodeBlockedUntil());
    }

    private static Usuario usuarioBase() {
        return Usuario.builder()
                .id(1L)
                .username("user")
                .email("user@test.com")
                .senha("hash")
                .loginCodeFailedAttempts(0)
                .build();
    }
}
