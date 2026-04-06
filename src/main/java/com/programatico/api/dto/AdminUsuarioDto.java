package com.programatico.api.dto;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public final class AdminUsuarioDto {

    private AdminUsuarioDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotNull(message = "Role é obrigatória")
        private TipoUsuario role;

        @NotNull(message = "Status ativo é obrigatório")
        private Boolean ativo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String username;
        private String email;
        private TipoUsuario role;
        private Boolean ativo;
        private Instant dataCriacao;

        public static Response fromEntity(Usuario u) {
            return Response.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .role(u.getRole())
                    .ativo(u.getAtivo())
                    .dataCriacao(u.getDataCriacao())
                    .build();
        }
    }
}
