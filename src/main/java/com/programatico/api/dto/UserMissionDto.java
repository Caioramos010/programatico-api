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
        private String title;
        private String type;
        private int currentProgress;
        private int goal;
        private int xpReward;
        private boolean completed;
    }
}
