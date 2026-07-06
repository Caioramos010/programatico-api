package com.programatico.api.dto;

import com.programatico.api.domain.enums.NivelHabilidade;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TrackDto {

    private TrackDto() {}

    public record NivelamentoRequest(@NotNull NivelHabilidade nivel) {}

    /** nivelInicial: 0 (iniciante), 10 (intermediário) ou 20 (avançado). */
    public record NivelamentoResponse(int nivelInicial, int modulosConcluidos) {}

    /**
     * Module with progress status calculated dynamically for the authenticated user.
     */
    public record ModuleWithProgress(
            Long id,
            String title,
            String type,
            int order,
            String status,
            String description,
            long totalXp,
            List<String> topAssuntos,
            boolean emAndamento
    ) {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String description;
        private String icon;
        private List<ModuleWithProgress> modules;
        private int completedPercentage;
        private int totalModules;
        private int completedModules;
    }
}
