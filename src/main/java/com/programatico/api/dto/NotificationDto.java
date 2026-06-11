package com.programatico.api.dto;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.enums.NotificationKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public final class NotificationDto {

    private NotificationDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String message;
        private NotificationKind kind;
        private Boolean read;
        private Instant createdAt;
        private Instant readAt;

        public static Response fromEntity(Notification notification) {
            return Response.builder()
                    .id(notification.getId())
                    .title(notification.getTitle())
                    .message(notification.getMessage())
                    .kind(notification.getKind())
                    .read(notification.getRead())
                    .createdAt(notification.getCreatedAt())
                    .readAt(notification.getReadAt())
                    .build();
        }
    }
}
