package com.programatico.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedDeviceServiceTest {

    private final TrustedDeviceService trustedDeviceService = new TrustedDeviceService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                trustedDeviceService,
                "secret",
                "programatico-api-secret-key-minimo-256-bits-para-hs256-seguro"
        );
        ReflectionTestUtils.setField(trustedDeviceService, "expirationDays", 30);
        ReflectionTestUtils.setField(trustedDeviceService, "secureCookie", false);
    }

    @Test
    void tokenValidoDeveSerAceitoParaUsuarioCorreto() {
        String token = trustedDeviceService.gerarToken(42L);
        assertTrue(trustedDeviceService.isConfiavel(42L, token));
    }

    @Test
    void tokenDeOutroUsuarioDeveSerRejeitado() {
        String token = trustedDeviceService.gerarToken(42L);
        assertFalse(trustedDeviceService.isConfiavel(99L, token));
    }

    @Test
    void tokenInvalidoDeveSerRejeitado() {
        assertFalse(trustedDeviceService.isConfiavel(1L, "token-invalido"));
    }
}
