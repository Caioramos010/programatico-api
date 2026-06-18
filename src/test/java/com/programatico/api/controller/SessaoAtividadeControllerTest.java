package com.programatico.api.controller;

import com.programatico.api.dto.SessaoDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.SessaoAtividadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessaoAtividadeController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessaoAtividadeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessaoAtividadeService sessaoAtividadeService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void iniciarPraticaFixacaoDeveRetornar200ComSessao() throws Exception {
        SessaoDto.InicioResponse response = SessaoDto.InicioResponse.builder()
                .sessionId(42L)
                .moduleTitle("Prática: Fixação")
                .initialLives(5)
                .totalExercises(5)
                .exercises(List.of(
                        SessaoDto.ExercicioSessao.builder()
                                .id(1L)
                                .order(1)
                                .statement("Enunciado 1")
                                .tipo("MULTIPLE_CHOICE")
                                .displayData("{\"options\":[]}")
                                .xpReward(3)
                                .relatedTopics(List.of())
                                .build()
                ))
                .build();

        when(sessaoAtividadeService.iniciarPratica(eq("fixacao"), eq("user"))).thenReturn(response);

        mockMvc.perform(post("/api/aprender/pratica/fixacao/iniciar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.moduleTitle").value("Prática: Fixação"))
                .andExpect(jsonPath("$.totalExercises").value(5))
                .andExpect(jsonPath("$.initialLives").value(5))
                .andExpect(jsonPath("$.exercises[0].statement").value("Enunciado 1"));
    }
}
