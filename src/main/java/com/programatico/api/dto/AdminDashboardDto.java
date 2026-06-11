package com.programatico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class AdminDashboardDto {

    private AdminDashboardDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private long totalUsers;
        private long activeSessions;
        private long totalModules;
        /** Crescimento de usuários nos últimos 30 dias, em % sobre a base anterior. */
        private int growthPercent;
    }
}
