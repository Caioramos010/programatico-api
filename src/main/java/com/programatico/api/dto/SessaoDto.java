package com.programatico.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class SessaoDto {

    private SessaoDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InicioResponse {
        private Long sessionId;
        private String moduleTitle;
        private int initialLives;
        private int totalExercises;
        /** Nulo em sessões normais; preenchido na prática CRONOMETRADO. */
        private Integer timeLimitSeconds;
        private List<ExercicioSessao> exercises;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExercicioSessao {
        private Long id;
        private int order;
        private String statement;
        private String tipo;
        private String displayData;
        private int xpReward;
        private List<String> relatedTopics;
        private String imageData;
        /** Preenchido por exercício na prática CRONOMETRADO; nulo nos demais modos. */
        private Integer timeLimitSeconds;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RespostaRequest {
        @NotNull(message = "ID do exercício é obrigatório")
        private Long exercicioId;

        @NotBlank(message = "Resposta é obrigatória")
        private String resposta;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RespostaResponse {
        private boolean correct;
        private String correctAnswer;
        private int remainingLives;
        private List<String> relatedTopics;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConclusaoResponse {
        private int xpEarned;
        private int accuracy;
        private int durationSeconds;
        private int remainingLives;
        private boolean moduleCompleted;
    }
}
