package com.programatico.api.dto;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.enums.NotificationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    public static class Request {
        @NotBlank(message = "Título é obrigatório")
        @Size(max = 255, message = "Título deve ter no máximo 255 caracteres")
        private String title;

        @NotBlank(message = "Mensagem é obrigatória")
        private String message;

        @NotNull(message = "Tipo é obrigatório")
        private NotificationKind kind;

        private Boolean read;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255, message = "Título deve ter no máximo 255 caracteres")
        private String title;

        private String message;

        private NotificationKind kind;

        private Boolean read;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long userId;
        private String title;
        private String message;
        private NotificationKind kind;
        private Boolean read;
        private Instant createdAt;
        private Instant readAt;

        public static Response fromEntity(Notification notification) {
            return Response.builder()
                    .id(notification.getId())
                    .userId(notification.getUsuario().getId())
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
