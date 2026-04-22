package com.programatico.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class SessaoDto {

    private SessaoDto() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InicioResponse {
        private Long sessaoId;
        private String tituloModulo;
        private int vidasIniciais;
        private int totalExercicios;
        private List<ExercicioSessao> exercicios;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExercicioSessao {
        private Long id;
        private int ordem;
        private String enunciado;
        private String tipo;
        private String dadosExibicao;
        private int xpRecompensa;
        private List<String> assuntosRelacionados;
        private String imagemData;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RespostaRequest {
        @NotNull(message = "ID do exercício é obrigatório")
        private Long exercicioId;

        @NotBlank(message = "Resposta é obrigatória")
        private String resposta;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RespostaResponse {
        private boolean correto;
        private String respostaCorreta;
        private int vidasRestantes;
        private List<String> assuntosRelacionados;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConclusaoResponse {
        private int xpGanho;
        private int taxaAcerto;
        private int duracaoSegundos;
        private int vidasRestantes;
        private boolean moduloConcluido;
    }
}
