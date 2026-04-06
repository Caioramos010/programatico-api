package com.programatico.api.controller;

import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.LearnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LearnController.class)
@AutoConfigureMockMvc(addFilters = false)
class LearnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearnService learnService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void getTrilhaDeveRetornar200ComTrilhaDadosValidos() throws Exception {
        TrackDto.Response response = TrackDto.Response.builder()
                .id(1L)
                .titulo("Lógica Básica")
                .descricao("Introdução")
                .percentualConcluido(0)
                .totalModulos(2)
                .concluidosModulos(0)
                .modulos(List.of())
                .build();

        when(learnService.getTrilhaComProgresso(anyString())).thenReturn(response);

        mockMvc.perform(get("/api/aprender/trilha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Lógica Básica"))
                .andExpect(jsonPath("$.percentualConcluido").value(0))
                .andExpect(jsonPath("$.totalModulos").value(2));
    }

    @Test
    @WithMockUser(username = "user")
    void getStatsDeveRetornar200ComEstatisticasDoUsuario() throws Exception {
        UserStatsDto.Response response = UserStatsDto.Response.builder()
                .totalXp(100)
                .vidasAtuais(5)
                .sequenciaAtual(2)
                .maxSequencia(5)
                .build();

        when(learnService.getEstatisticas(anyString())).thenReturn(response);

        mockMvc.perform(get("/api/aprender/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").value(100))
                .andExpect(jsonPath("$.vidasAtuais").value(5));
    }

    @Test
    @WithMockUser(username = "user")
    void getMissoesDeveRetornar200ComListaDeMissoes() throws Exception {
        UserMissionDto.Response missao = UserMissionDto.Response.builder()
                .missionId(1L)
                .titulo("Complete 3 módulos")
                .progressoAtual(1)
                .meta(3)
                .recompensaXp(10)
                .concluida(false)
                .build();

        when(learnService.getMissoes(anyString())).thenReturn(List.of(missao));

        mockMvc.perform(get("/api/aprender/missoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Complete 3 módulos"))
                .andExpect(jsonPath("$[0].progressoAtual").value(1))
                .andExpect(jsonPath("$[0].recompensaXp").value(10));
    }
}
