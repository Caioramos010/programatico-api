package com.programatico.api.service;

import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Regras de vidas: máximo, perda e recarga on-read (1 vida a cada intervalo,
 * ancorada em UserStats.livesUpdatedAt). Não há scheduler — a recarga é
 * aplicada sempre que as vidas são lidas ou alteradas.
 */
@Service
public class VidasService {

    public static final int MAX_VIDAS = 5;
    public static final Duration INTERVALO_RECARGA = Duration.ofMinutes(30);

    /** Root com assinatura ativa (não expirada). É o gate canônico de funções Root. */
    public boolean isRootAtivo(Usuario usuario) {
        if (usuario.getSubscriptionType() != SubscriptionType.ROOT) {
            return false;
        }
        Instant expiraEm = usuario.getSubscriptionExpiresAt();
        return expiraEm == null || expiraEm.isAfter(Instant.now());
    }

    public boolean temVidasIlimitadas(Usuario usuario) {
        return isRootAtivo(usuario);
    }

    /** Aplica recarga pendente ao stats (mutação em memória; o caller persiste). */
    public void aplicarRecarga(UserStats stats) {
        int vidas = vidasAtuais(stats);
        if (vidas >= MAX_VIDAS) {
            stats.setLivesUpdatedAt(null);
            return;
        }
        LocalDateTime marco = stats.getLivesUpdatedAt();
        if (marco == null) {
            // Linha legada (anterior à recarga): inicia o relógio agora.
            stats.setLivesUpdatedAt(LocalDateTime.now());
            return;
        }
        long decorrido = Duration.between(marco, LocalDateTime.now()).getSeconds();
        long ganhas = decorrido / INTERVALO_RECARGA.getSeconds();
        if (ganhas <= 0) {
            return;
        }
        int novasVidas = (int) Math.min(MAX_VIDAS, vidas + ganhas);
        stats.setCurrentLives(novasVidas);
        stats.setLivesUpdatedAt(novasVidas >= MAX_VIDAS
                ? null
                : marco.plusSeconds(ganhas * INTERVALO_RECARGA.getSeconds()));
    }

    /** Decrementa 1 vida e ancora o relógio de recarga se ele ainda não estiver correndo. */
    public void registrarPerdaDeVida(UserStats stats) {
        int vidas = vidasAtuais(stats);
        stats.setCurrentLives(Math.max(0, vidas - 1));
        if (stats.getLivesUpdatedAt() == null) {
            stats.setLivesUpdatedAt(LocalDateTime.now());
        }
    }

    /** Segundos até a próxima vida, ou null se as vidas estão cheias. */
    public Long segundosAteProximaVida(UserStats stats) {
        if (vidasAtuais(stats) >= MAX_VIDAS || stats.getLivesUpdatedAt() == null) {
            return null;
        }
        long decorrido = Duration.between(stats.getLivesUpdatedAt(), LocalDateTime.now()).getSeconds();
        return Math.max(0, INTERVALO_RECARGA.getSeconds() - decorrido);
    }

    private int vidasAtuais(UserStats stats) {
        return stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS;
    }
}
