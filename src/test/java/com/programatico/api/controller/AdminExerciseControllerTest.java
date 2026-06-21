package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.dto.ExerciseDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminExerciseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminExerciseController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminExerciseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminExerciseService adminExerciseService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private ExerciseDto.Response sampleResponse() {
        return ExerciseDto.Response.builder()
                .id(1L)
                .moduloId(10L)
                .statement("Qual a saída?")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(10)
                .build();
    }

    private ExerciseDto.Request sampleRequest() {
        return ExerciseDto.Request.builder()
                .statement("Qual a saída?")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(10)
                .build();
    }

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminExerciseService.listarPorModulo(10L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/modulos/10/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].exerciseType").value("MULTIPLE_CHOICE"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        when(adminExerciseService.criar(eq(10L), any(ExerciseDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/modulos/10/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.xpReward").value(10));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "statement": "", "exerciseData": "", "xpReward": 0 }
                """;

        mockMvc.perform(post("/api/admin/modulos/10/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        when(adminExerciseService.atualizar(eq(1L), any(ExerciseDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/exercises/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statement").value("Qual a saída?"));
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        when(adminExerciseService.atualizar(eq(99L), any(ExerciseDto.Request.class)))
                .thenThrow(new ResourceNotFoundException("Exercise", 99L));

        mockMvc.perform(put("/api/admin/exercises/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminExerciseService).deletar(1L);

        mockMvc.perform(delete("/api/admin/exercises/1"))
                .andExpect(status().isNoContent());
    }
}
