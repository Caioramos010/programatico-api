package com.programatico.api.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Geração e proteção dos códigos de acesso enviados por e-mail (ativação,
 * verificação de login, redefinição de senha e exclusão de conta).
 *
 * Decisões (feedback da banca + revisão interna):
 * - Instância única de {@link SecureRandom} (thread-safe; evita o custo de
 *   re-semear a cada código).
 * - Alfabeto alfanumérico sem caracteres ambíguos (sem I, O, 0 e 1):
 *   32 símbolos → 32^6 ≈ 1,07 bilhão de combinações num código de 6 posições
 *   (~1000× o espaço de 6 dígitos numéricos).
 * - O banco guarda apenas o SHA-256 do código (nunca texto puro): quem lê a
 *   base não consegue usar os códigos pendentes. O hash é determinístico para
 *   permitir lookup; a força vem do espaço de busca + expiração + limite de
 *   tentativas (VerificationCodeGuardService).
 */
public final class CodigoAcesso {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private CodigoAcesso() {}

    public static String gerar(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            sb.append(ALFABETO[RANDOM.nextInt(ALFABETO.length)]);
        }
        return sb.toString();
    }

    /**
     * SHA-256 (hex) do código normalizado (trim + maiúsculas — o usuário pode
     * digitar minúsculas). Códigos legados numéricos não são afetados pela
     * normalização.
     */
    public static String hash(String codigo) {
        if (codigo == null) {
            return null;
        }
        String normalizado = codigo.trim().toUpperCase();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalizado.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
