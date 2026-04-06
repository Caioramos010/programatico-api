package com.programatico.api.dto;

import com.programatico.api.domain.UserStats;
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
        private int vidasAtuais;
        private int sequenciaAtual;
        private int maxSequencia;

        /** Retorna stats zerados com vidas padrão (5) para usuários sem registro. */
        public static Response padrao() {
            return Response.builder()
                    .totalXp(0)
                    .vidasAtuais(5)
                    .sequenciaAtual(0)
                    .maxSequencia(0)
                    .build();
        }

        public static Response fromEntity(UserStats stats) {
            return Response.builder()
                    .totalXp(stats.getTotalXp() != null ? stats.getTotalXp() : 0)
                    .vidasAtuais(stats.getCurrentLives() != null ? stats.getCurrentLives() : 5)
                    .sequenciaAtual(stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0)
                    .maxSequencia(stats.getHighestStreak() != null ? stats.getHighestStreak() : 0)
                    .build();
        }
    }
}
