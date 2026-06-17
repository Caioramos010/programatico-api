package com.programatico.api.dto;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.enums.LayoutType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ContentBlockDto {

    private ContentBlockDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotNull(message = "Tipo de layout é obrigatório")
        private LayoutType layoutType;

        @Size(max = 5000, message = "Conteúdo de texto deve ter no máximo 5000 caracteres")
        private String textContent;

        @NotNull(message = "Ordem de exibição é obrigatória")
        @Min(value = 0, message = "Ordem de exibição não pode ser negativa")
        private Integer displayOrder;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long moduloId;
        private Long paginaId;
        private LayoutType layoutType;
        private String textContent;
        private Integer displayOrder;

        public static Response fromEntity(ContentBlock block) {
            return Response.builder()
                    .id(block.getId())
                    .moduloId(block.getModulo().getId())
                    .paginaId(block.getPagina() != null ? block.getPagina().getId() : null)
                    .layoutType(block.getLayoutType())
                    .textContent(block.getTextContent())
                    .displayOrder(block.getDisplayOrder())
                    .build();
        }
    }
}
