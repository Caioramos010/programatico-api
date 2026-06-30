package com.programatico.api.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class TotpService {

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setTimeStepSizeInMillis(30_000L)
                    .setWindowSize(1)
                    .build()
    );

    @Value("${app.totp.issuer:Programático}")
    private String issuer;

    public String gerarSecret() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        return key.getKey();
    }

    public String montarOtpAuthUrl(String email, String secret) {
        GoogleAuthenticatorKey key = new GoogleAuthenticatorKey.Builder(secret).build();
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(issuer, email, key);
    }

    public String gerarQrCodeDataUri(String email, String secret) {
        String otpAuthUrl = montarOtpAuthUrl(email, secret);
        byte[] png = QrCodePngGenerator.gerar(otpAuthUrl, 256, 256);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    public boolean codigoValido(String secret, String codigo) {
        if (secret == null || secret.isBlank() || codigo == null || codigo.isBlank()) {
            return false;
        }
        try {
            int code = Integer.parseInt(codigo.trim());
            return googleAuthenticator.authorize(secret.trim(), code);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
