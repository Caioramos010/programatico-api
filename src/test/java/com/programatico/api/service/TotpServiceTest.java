package com.programatico.api.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();
    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setTimeStepSizeInMillis(30_000L)
                    .setWindowSize(1)
                    .build()
    );

    @BeforeEach
    void configurarIssuer() {
        ReflectionTestUtils.setField(totpService, "issuer", "Programático Teste");
    }

    @Test
    void deveValidarCodigoGeradoParaSecret() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();
        int codigo = googleAuthenticator.getTotpPassword(secret);

        assertTrue(totpService.codigoValido(secret, String.format("%06d", codigo)));
    }

    @Test
    void deveRejeitarCodigoInvalido() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        assertFalse(totpService.codigoValido(key.getKey(), "000000"));
    }

    @Test
    void deveGerarQrCodeDataUri() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();
        String dataUri = totpService.gerarQrCodeDataUri("user@test.com", secret);

        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/png;base64,"));
        assertTrue(totpService.montarOtpAuthUrl("user@test.com", secret).contains("otpauth://"));
    }
}
