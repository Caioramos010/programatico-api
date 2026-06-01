package com.programatico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TrackDto {

    private TrackDto() {}

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
            long totalXp
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
