package com.programatico.api.dto;

import com.programatico.api.domain.TeoriaPagina;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class TeoriaPaginaDto {

    private TeoriaPaginaDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotBlank(message = "Título é obrigatório")
        private String title;

        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long moduloId;
        private String title;
        private String description;
        private Integer displayOrder;
        private long totalBlocos;

        public static Response fromEntity(TeoriaPagina pagina, long totalBlocos) {
            return Response.builder()
                    .id(pagina.getId())
                    .moduloId(pagina.getModulo().getId())
                    .title(pagina.getTitle())
                    .description(pagina.getDescription())
                    .displayOrder(pagina.getDisplayOrder())
                    .totalBlocos(totalBlocos)
                    .build();
        }
    }
}
