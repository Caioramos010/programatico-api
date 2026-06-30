package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.MissaoDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminMissaoService;
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

@WebMvcTest(AdminMissaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminMissaoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AdminMissaoService adminMissaoService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminMissaoService.listarTodas()).thenReturn(List.of(
                MissaoDto.Response.builder().id(1L).title("Missão 1").xpReward(10).build()));

        mockMvc.perform(get("/api/admin/missoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Missão 1"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        MissaoDto.Request request = MissaoDto.Request.builder()
                .title("Nova").objectiveType("XP").xpReward(5).quantity(10).build();
        when(adminMissaoService.criar(any())).thenReturn(
                MissaoDto.Response.builder().id(2L).title("Nova").build());

        mockMvc.perform(post("/api/admin/missoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nova"));
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminMissaoService).deletar(1L);
        mockMvc.perform(delete("/api/admin/missoes/1")).andExpect(status().isNoContent());
    }
}
