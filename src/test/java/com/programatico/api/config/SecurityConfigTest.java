package com.programatico.api.config;

import com.programatico.api.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock private JwtAuthFilter jwtAuthFilter;

    @Test
    void corsConfigurationSourceDeveIncluirOrigensMetodosECredenciais() {
        SecurityConfig securityConfig = new SecurityConfig(jwtAuthFilter);
        ReflectionTestUtils.setField(securityConfig, "allowedOriginsRaw",
                "http://localhost:5173,http://example.com");
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatternsRaw", "http://localhost:*");

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/test"));

        assertTrue(cors.getAllowedOrigins().contains("http://localhost:5173"));
        assertTrue(cors.getAllowedOrigins().contains("http://example.com"));
        assertTrue(cors.getAllowedOriginPatterns().contains("http://localhost:*"));
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertTrue(cors.getAllowedMethods().contains("PATCH"));
        assertTrue(cors.getAllowCredentials());
        assertTrue(cors.getAllowedHeaders().contains("*"));
    }

    @Test
    void passwordEncoderDeveSerBCrypt() {
        SecurityConfig securityConfig = new SecurityConfig(jwtAuthFilter);
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String hash = encoder.encode("Senha@123");
        assertNotEquals("Senha@123", hash);
        assertTrue(encoder.matches("Senha@123", hash));
    }
}
