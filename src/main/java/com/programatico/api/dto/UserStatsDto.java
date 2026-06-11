package com.programatico.api.dto;

import com.programatico.api.domain.UserStats;
import com.programatico.api.service.VidasService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class UserStatsDto {

    private UserStatsDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private int totalXp;
        private int currentLives;
        private int maxLives;
        /** Segundos até a próxima vida; null quando as vidas estão cheias ou são ilimitadas. */
        private Long secondsUntilNextLife;
        /** Intervalo de recarga de uma vida, em segundos. */
        private long secondsPerLife;
        private boolean unlimitedLives;
        private int currentStreak;
        private int maxStreak;

        /** Zeroed stats with default lives (5) for users without a stats row. */
        public static Response padrao(boolean unlimitedLives) {
            return Response.builder()
                    .totalXp(0)
                    .currentLives(VidasService.MAX_VIDAS)
                    .maxLives(VidasService.MAX_VIDAS)
                    .secondsUntilNextLife(null)
                    .secondsPerLife(VidasService.INTERVALO_RECARGA.getSeconds())
                    .unlimitedLives(unlimitedLives)
                    .currentStreak(0)
                    .maxStreak(0)
                    .build();
        }

        public static Response fromEntity(UserStats stats, Long secondsUntilNextLife, boolean unlimitedLives) {
            return Response.builder()
                    .totalXp(stats.getTotalXp() != null ? stats.getTotalXp() : 0)
                    .currentLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : VidasService.MAX_VIDAS)
                    .maxLives(VidasService.MAX_VIDAS)
                    .secondsUntilNextLife(unlimitedLives ? null : secondsUntilNextLife)
                    .secondsPerLife(VidasService.INTERVALO_RECARGA.getSeconds())
                    .unlimitedLives(unlimitedLives)
                    .currentStreak(stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0)
                    .maxStreak(stats.getHighestStreak() != null ? stats.getHighestStreak() : 0)
                    .build();
        }
    }
}
