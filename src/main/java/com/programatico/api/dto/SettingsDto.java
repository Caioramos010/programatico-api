package com.programatico.api.dto;

import com.programatico.api.domain.UserSettings;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class SettingsDto {

    private SettingsDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferencesResponse {
        private boolean disableUpdateNotifications;
        private boolean disableDaystreakNotifications;
        private boolean disableMissionNotifications;
        private boolean disableSubscriptionNotifications;
        private boolean disableEmailNotifications;
        private boolean disableAllNotifications;

        public static NotificationPreferencesResponse fromEntity(UserSettings settings) {
            return NotificationPreferencesResponse.builder()
                    .disableUpdateNotifications(Boolean.TRUE.equals(settings.getDisableUpdateNotifications()))
                    .disableDaystreakNotifications(Boolean.TRUE.equals(settings.getDisableDaystreakNotifications()))
                    .disableMissionNotifications(Boolean.TRUE.equals(settings.getDisableMissionNotifications()))
                    .disableSubscriptionNotifications(Boolean.TRUE.equals(settings.getDisableSubscriptionNotifications()))
                    .disableEmailNotifications(Boolean.TRUE.equals(settings.getDisableEmailNotifications()))
                    .disableAllNotifications(Boolean.TRUE.equals(settings.getDisableAllNotifications()))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferencesRequest {
        @NotNull
        private Boolean disableUpdateNotifications;

        @NotNull
        private Boolean disableDaystreakNotifications;

        @NotNull
        private Boolean disableMissionNotifications;

        @NotNull
        private Boolean disableSubscriptionNotifications;

        @NotNull
        private Boolean disableEmailNotifications;

        @NotNull
        private Boolean disableAllNotifications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityPreferencesResponse {
        private boolean twoFactorEnabled;

        public static SecurityPreferencesResponse fromEntity(UserSettings settings) {
            return SecurityPreferencesResponse.builder()
                    .twoFactorEnabled(Boolean.TRUE.equals(settings.getTwoFactorEnabled()))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityPreferencesRequest {
        @NotNull
        private Boolean twoFactorEnabled;
    }
}
