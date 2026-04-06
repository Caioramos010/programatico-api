package com.programatico.api.dto;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.enums.ModuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ModuloDto {

    private ModuloDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Título é obrigatório")
        private String title;

        @NotNull(message = "Tipo de módulo é obrigatório")
        private ModuleType moduleType;

        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long trackId;
        private String title;
        private ModuleType moduleType;
        private Integer displayOrder;
        private String description;
        private Long totalComponentes;
        private Long totalXp;

        public static Response fromEntity(Modulo modulo, long totalComponentes, long totalXp) {
            return Response.builder()
                    .id(modulo.getId())
                    .trackId(modulo.getTrack().getId())
                    .title(modulo.getTitle())
                    .moduleType(modulo.getModuleType())
                    .displayOrder(modulo.getDisplayOrder())
                    .description(modulo.getDescription())
                    .totalComponentes(totalComponentes)
                    .totalXp(totalXp)
                    .build();
        }
    }
}
