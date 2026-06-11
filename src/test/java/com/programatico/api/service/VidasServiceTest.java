package com.programatico.api.service;

import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VidasServiceTest {

    private VidasService vidasService;

    @BeforeEach
    void setUp() {
        vidasService = new VidasService();
    }

    private UserStats stats(Integer vidas, LocalDateTime marco) {
        return UserStats.builder().currentLives(vidas).livesUpdatedAt(marco).build();
    }

    @Test
    void naoRecarregaAntesDoIntervalo() {
        UserStats stats = stats(3, LocalDateTime.now().minusMinutes(10));

        vidasService.aplicarRecarga(stats);

        assertEquals(3, stats.getCurrentLives());
        assertNotNull(stats.getLivesUpdatedAt());
    }

    @Test
    void recarregaUmaVidaPorIntervalo() {
        LocalDateTime marco = LocalDateTime.now().minusMinutes(65);
        UserStats stats = stats(2, marco);

        vidasService.aplicarRecarga(stats);

        assertEquals(4, stats.getCurrentLives());
        // marco avança 2 intervalos completos; sobram ~5 min decorridos para a próxima vida
        assertEquals(marco.plusMinutes(60), stats.getLivesUpdatedAt());
    }

    @Test
    void recargaNaoUltrapassaMaximoEZeraMarco() {
        UserStats stats = stats(4, LocalDateTime.now().minusHours(10));

        vidasService.aplicarRecarga(stats);

        assertEquals(VidasService.MAX_VIDAS, stats.getCurrentLives());
        assertNull(stats.getLivesUpdatedAt());
    }

    @Test
    void vidasCheiasLimpamMarco() {
        UserStats stats = stats(VidasService.MAX_VIDAS, LocalDateTime.now().minusMinutes(5));

        vidasService.aplicarRecarga(stats);

        assertEquals(VidasService.MAX_VIDAS, stats.getCurrentLives());
        assertNull(stats.getLivesUpdatedAt());
    }

    @Test
    void linhaLegadaSemMarcoIniciaRelogioSemRecarregar() {
        UserStats stats = stats(2, null);

        vidasService.aplicarRecarga(stats);

        assertEquals(2, stats.getCurrentLives());
        assertNotNull(stats.getLivesUpdatedAt());
    }

    @Test
    void perdaDeVidaComVidasCheiasAncoraRelogio() {
        UserStats stats = stats(VidasService.MAX_VIDAS, null);

        vidasService.registrarPerdaDeVida(stats);

        assertEquals(VidasService.MAX_VIDAS - 1, stats.getCurrentLives());
        assertNotNull(stats.getLivesUpdatedAt());
    }

    @Test
    void perdaDeVidaNaoReancoraRelogioJaCorrendo() {
        LocalDateTime marco = LocalDateTime.now().minusMinutes(10);
        UserStats stats = stats(3, marco);

        vidasService.registrarPerdaDeVida(stats);

        assertEquals(2, stats.getCurrentLives());
        assertEquals(marco, stats.getLivesUpdatedAt());
    }

    @Test
    void perdaDeVidaNaoFicaNegativa() {
        UserStats stats = stats(0, LocalDateTime.now());

        vidasService.registrarPerdaDeVida(stats);

        assertEquals(0, stats.getCurrentLives());
    }

    @Test
    void segundosAteProximaVidaDentroDoIntervalo() {
        UserStats stats = stats(3, LocalDateTime.now().minusMinutes(10));

        Long segundos = vidasService.segundosAteProximaVida(stats);

        assertNotNull(segundos);
        long esperado = VidasService.INTERVALO_RECARGA.getSeconds() - 10 * 60;
        assertTrue(Math.abs(segundos - esperado) <= 2,
                "esperado ~" + esperado + "s, obtido " + segundos + "s");
    }

    @Test
    void segundosAteProximaVidaNuloComVidasCheias() {
        UserStats stats = stats(VidasService.MAX_VIDAS, null);

        assertNull(vidasService.segundosAteProximaVida(stats));
    }

    @Test
    void rootAtivoTemVidasIlimitadas() {
        Usuario semExpiracao = Usuario.builder()
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(null)
                .build();
        Usuario vigente = Usuario.builder()
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertTrue(vidasService.temVidasIlimitadas(semExpiracao));
        assertTrue(vidasService.temVidasIlimitadas(vigente));
    }

    @Test
    void rootExpiradoOuFreeNaoTemVidasIlimitadas() {
        Usuario expirado = Usuario.builder()
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(Instant.now().minusSeconds(3600))
                .build();
        Usuario free = Usuario.builder()
                .subscriptionType(SubscriptionType.FREE)
                .build();

        assertFalse(vidasService.temVidasIlimitadas(expirado));
        assertFalse(vidasService.temVidasIlimitadas(free));
    }
}
