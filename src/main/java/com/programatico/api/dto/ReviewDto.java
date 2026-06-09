package com.programatico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ReviewDto {

    private ReviewDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long selectedTrackId;
        private int selectedDays;
        private int currentXp;
        private List<TrackOption> availableTracks;
        private List<StatCard> stats;
        private List<PerformancePoint> performanceData;
        private List<SubjectAccuracyItem> subjectAccuracy;
        private List<ErrorBySubjectItem> errorsBySubject;
        private List<ReviewNowItem> reviewNow;
        private List<RecentMissionItem> recentMissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackOption {
        private Long id;
        private String title;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatCard {
        private String title;
        private String value;
        private String subtitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformancePoint {
        private String day;
        private int acertos;
        private int erros;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectAccuracyItem {
        private String assunto;
        private int percentual;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorBySubjectItem {
        private String assunto;
        private int erros;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewNowItem {
        private String assunto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentMissionItem {
        private String label;
        private String status;
    }
}
