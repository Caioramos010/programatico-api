package com.programatico.api.dto;

import com.programatico.api.domain.Track;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class TrilhaDto {

    private TrilhaDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Título é obrigatório")
        private String title;

        @NotBlank(message = "Descrição é obrigatória")
        private String description;

        private String icon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String description;
        private Integer displayOrder;
        private String icon;
        private Long totalModulos;

        public static Response fromEntity(Track track, long totalModulos) {
            return Response.builder()
                    .id(track.getId())
                    .title(track.getTitle())
                    .description(track.getDescription())
                    .displayOrder(track.getDisplayOrder())
                    .icon(track.getIcon())
                    .totalModulos(totalModulos)
                    .build();
        }
    }
}
