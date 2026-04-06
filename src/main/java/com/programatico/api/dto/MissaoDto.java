package com.programatico.api.dto;

import com.programatico.api.domain.Mission;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class MissaoDto {

    private MissaoDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Título é obrigatório")
        private String title;

        @NotBlank(message = "Tipo de objetivo é obrigatório")
        private String objectiveType;

        @NotNull(message = "Recompensa XP é obrigatória")
        @Min(value = 1, message = "XP deve ser maior que zero")
        private Integer xpReward;

        @NotNull(message = "Quantidade é obrigatória")
        @Min(value = 1, message = "Quantidade deve ser maior que zero")
        private Integer quantidade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String objectiveType;
        private Integer xpReward;
        private Integer quantidade;

        public static Response fromEntity(Mission mission) {
            return Response.builder()
                    .id(mission.getId())
                    .title(mission.getTitle())
                    .objectiveType(mission.getObjectiveType())
                    .xpReward(mission.getXpReward())
                    .quantidade(mission.getQuantidade())
                    .build();
        }
    }
}
