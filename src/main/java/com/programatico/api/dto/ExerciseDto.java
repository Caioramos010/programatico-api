package com.programatico.api.dto;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.enums.ExerciseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ExerciseDto {

    private ExerciseDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Enunciado é obrigatório")
        private String statement;

        @NotNull(message = "Tipo de exercício é obrigatório")
        private ExerciseType exerciseType;

        @NotBlank(message = "Dados do exercício são obrigatórios")
        private String exerciseData;

        @NotNull(message = "XP é obrigatório")
        private Integer xpReward;

        private String tags;
        private String imageData;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long moduloId;
        private String statement;
        private ExerciseType exerciseType;
        private String exerciseData;
        private Integer xpReward;
        private String tags;
        private String imageData;

        public static Response fromEntity(Exercise exercise) {
            return Response.builder()
                    .id(exercise.getId())
                    .moduloId(exercise.getModulo().getId())
                    .statement(exercise.getStatement())
                    .exerciseType(exercise.getExerciseType())
                    .exerciseData(exercise.getExerciseData())
                    .xpReward(exercise.getXpReward())
                    .tags(exercise.getTags())
                    .imageData(exercise.getImageData())
                    .build();
        }
    }
}
