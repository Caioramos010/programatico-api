package com.programatico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TrackDto {

    private TrackDto() {}

    /**
     * Módulo com status de progresso calculado dinamicamente para o usuário autenticado.
     */
    public record ModuloComProgresso(
            Long id,
            String titulo,
            String tipo,
            int ordem,
            String status,
            String descricao,
            long totalXp
    ) {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String titulo;
        private String descricao;
        private String icon;
        private List<ModuloComProgresso> modulos;
        private int percentualConcluido;
        private int totalModulos;
        private int concluidosModulos;
    }
}
