package com.programatico.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class TrustedDeviceService {

    public static final String COOKIE_NAME = "programatico_device_trust";
    private static final String TOKEN_TYPE = "device_trust";

    @Value("${jwt.secret:programatico-api-secret-key-mínimo-256-bits-para-hs256}")
    private String secret;

    @Value("${app.device-trust.expiration-days:30}")
    private int expirationDays;

    @Value("${app.device-trust.secure-cookie:false}")
    private boolean secureCookie;

    public boolean isConfiavel(Long usuarioId, String token) {
        if (usuarioId == null || token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
                return false;
            }
            Long tokenUserId = claims.get("usuarioId", Long.class);
            if (tokenUserId == null) {
                Object raw = claims.get("usuarioId");
                if (raw instanceof Integer integer) {
                    tokenUserId = integer.longValue();
                }
            }
            return usuarioId.equals(tokenUserId);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void definirCookie(HttpServletResponse response, Long usuarioId) {
        String token = gerarToken(usuarioId);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofDays(expirationDays))
                .sameSite(secureCookie ? "None" : "Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void removerCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite(secureCookie ? "None" : "Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    String gerarToken(Long usuarioId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofDays(expirationDays).toMillis());
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("type", TOKEN_TYPE)
                .claim("usuarioId", usuarioId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
