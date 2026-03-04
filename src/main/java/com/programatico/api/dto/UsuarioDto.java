package com.programatico.api.dto;

import com.programatico.api.domain.Usuario;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTOs de usuário (request e response) no padrão entidade/arquivo.
 */
public final class UsuarioDto {

    private UsuarioDto() {}

    // ---------- Request ----------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "E-mail ou nome de usuário é obrigatório")
        private String emailOuUsername;

        @NotBlank(message = "Senha é obrigatória")
        private String senha;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistroRequest {
        @NotBlank(message = "Nome de usuário é obrigatório")
        @Size(min = 2, max = 100)
        private String username;

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        private String email;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        private String senha;

        @Min(1)
        @Max(120)
        private Integer idade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivacaoRequest {
        @NotBlank(message = "Código de ativação é obrigatório")
        private String codigo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolicitarRedefinicaoSenhaRequest {
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NovaSenhaRequest {
        @NotBlank(message = "Código é obrigatório")
        private String codigo;

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        private String novaSenha;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(min = 2, max = 100)
        private String username;

        @Email(message = "E-mail inválido")
        private String email;

        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        private String senha;

        @Min(1)
        @Max(120)
        private Integer idade;
    }

    // ---------- Response ----------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String username;
        private String email;
        private Integer idade;
        private Boolean ativo;
        private Instant dataCriacao;

        public static Response fromEntity(Usuario u) {
            return Response.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .idade(u.getIdade())
                    .ativo(u.getAtivo())
                    .dataCriacao(u.getDataCriacao())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String tipo;
        private Response usuario;

        public LoginResponse(String token, Response usuario) {
            this.token = token;
            this.tipo = "Bearer";
            this.usuario = usuario;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String mensagem;

        public static MessageResponse of(String mensagem) {
            return new MessageResponse(mensagem);
        }
    }
}
