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
        private int currentLives;
        private int currentStreak;
        private int maxStreak;

        /** Zeroed stats with default lives (5) for users without a stats row. */
        public static Response padrao() {
            return Response.builder()
                    .totalXp(0)
                    .currentLives(5)
                    .currentStreak(0)
                    .maxStreak(0)
                    .build();
        }

        public static Response fromEntity(UserStats stats) {
            return Response.builder()
                    .totalXp(stats.getTotalXp() != null ? stats.getTotalXp() : 0)
                    .currentLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : 5)
                    .currentStreak(stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0)
                    .maxStreak(stats.getHighestStreak() != null ? stats.getHighestStreak() : 0)
                    .build();
        }
    }
}
