package com.programatico.api.controller;

import com.programatico.api.dto.TheoryDto;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .title("Lógica Básica")
                .description("Introdução")
                .completedPercentage(0)
                .totalModules(2)
                .completedModules(0)
                .modules(List.of())
                .build();

        when(learnService.getTrilhaComProgresso(anyString())).thenReturn(response);

        mockMvc.perform(get("/api/aprender/trilha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lógica Básica"))
                .andExpect(jsonPath("$.completedPercentage").value(0))
                .andExpect(jsonPath("$.totalModules").value(2));
    }

    @Test
    @WithMockUser(username = "user")
    void getStatsDeveRetornar200ComEstatisticasDoUsuario() throws Exception {
        UserStatsDto.Response response = UserStatsDto.Response.builder()
                .totalXp(100)
                .currentLives(5)
                .currentStreak(2)
                .maxStreak(5)
                .build();

        when(learnService.getEstatisticas(anyString())).thenReturn(response);

        mockMvc.perform(get("/api/aprender/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").value(100))
                .andExpect(jsonPath("$.currentLives").value(5));
    }

    @Test
    @WithMockUser(username = "user")
    void getMissoesDeveRetornar200ComListaDeMissoes() throws Exception {
        UserMissionDto.Response missao = UserMissionDto.Response.builder()
                .missionId(1L)
                .title("Complete 3 módulos")
                .currentProgress(1)
                .goal(3)
                .xpReward(10)
                .completed(false)
                .build();

        when(learnService.getMissoes(anyString())).thenReturn(List.of(missao));

        mockMvc.perform(get("/api/aprender/missoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Complete 3 módulos"))
                .andExpect(jsonPath("$[0].currentProgress").value(1))
                .andExpect(jsonPath("$[0].xpReward").value(10));
    }

    @Test
    @WithMockUser(username = "user")
    void getTeoricoDeveRetornar200() throws Exception {
        TheoryDto.Response response = TheoryDto.Response.builder()
                .moduleId(1L)
                .moduleTitle("Teoria")
                .pages(List.of())
                .build();
        when(learnService.getTeorico(anyLong(), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/aprender/modulos/1/teorico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Teoria"));
    }

    @Test
    @WithMockUser(username = "user")
    void concluirTeoricoDeveRetornar204() throws Exception {
        doNothing().when(learnService).concluirTeorico(anyLong(), anyString());

        mockMvc.perform(post("/api/aprender/modulos/1/teorico/concluir"))
                .andExpect(status().isNoContent());
    }
}
