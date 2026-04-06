package com.programatico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class UserMissionDto {

    private UserMissionDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long missionId;
        private String titulo;
        private String tipo;
        private int progressoAtual;
        private int meta;
        private int recompensaXp;
        private boolean concluida;
    }
}
