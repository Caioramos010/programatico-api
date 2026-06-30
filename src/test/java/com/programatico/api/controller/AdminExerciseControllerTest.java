package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.dto.ExerciseDto;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminExerciseController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminExerciseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AdminExerciseService adminExerciseService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminExerciseService.listarPorModulo(1L)).thenReturn(List.of(
                ExerciseDto.Response.builder().id(1L).statement("Q1").exerciseType(ExerciseType.MULTIPLE_CHOICE).build()));

        mockMvc.perform(get("/api/admin/modulos/1/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statement").value("Q1"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        ExerciseDto.Request request = ExerciseDto.Request.builder()
                .statement("Novo").exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}").xpReward(5).build();
        when(adminExerciseService.criar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(ExerciseDto.Response.builder().id(2L).statement("Novo").build());

        mockMvc.perform(post("/api/admin/modulos/1/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statement").value("Novo"));
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminExerciseService).deletar(1L);
        mockMvc.perform(delete("/api/admin/exercises/1")).andExpect(status().isNoContent());
    }
}
