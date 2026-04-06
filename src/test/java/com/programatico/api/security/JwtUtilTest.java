package com.programatico.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void deveGerarTokenValidoERecuperarClaims() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "programatico-api-secret-key-minimo-256-bits-para-hs256-seguro");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 60000L);

        String token = jwtUtil.gerarToken("usuario-teste", 42L);

        assertTrue(jwtUtil.validarToken(token));
        assertEquals("usuario-teste", jwtUtil.getUsernameFromToken(token));
        assertEquals(42L, jwtUtil.getUsuarioIdFromToken(token));
    }

    @Test
    void deveRetornarFalseQuandoTokenInvalido() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "programatico-api-secret-key-minimo-256-bits-para-hs256-seguro");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 60000L);

        assertFalse(jwtUtil.validarToken("token-invalido"));
    }
}
