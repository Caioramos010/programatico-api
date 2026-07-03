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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Test
    @WithMockUser(username = "user")
    void iniciarPraticaCronometradaDeveRetornar200ComTempoPorExercicio() throws Exception {
        SessaoDto.InicioResponse response = SessaoDto.InicioResponse.builder()
                .sessionId(77L)
                .moduleTitle("Prática: Cronometrado")
                .initialLives(5)
                .totalExercises(2)
                .exercises(List.of(
                        SessaoDto.ExercicioSessao.builder()
                                .id(1L)
                                .order(1)
                                .statement("Enunciado 3xp")
                                .tipo("MULTIPLE_CHOICE")
                                .displayData("{\"options\":[]}")
                                .xpReward(3)
                                .timeLimitSeconds(60)
                                .relatedTopics(List.of())
                                .build(),
                        SessaoDto.ExercicioSessao.builder()
                                .id(2L)
                                .order(2)
                                .statement("Enunciado 7xp")
                                .tipo("MULTIPLE_CHOICE")
                                .displayData("{\"options\":[]}")
                                .xpReward(7)
                                .timeLimitSeconds(120)
                                .relatedTopics(List.of())
                                .build()
                ))
                .build();

        when(sessaoAtividadeService.iniciarPratica(eq("cronometrado"), eq("user"))).thenReturn(response);

        mockMvc.perform(post("/api/aprender/pratica/cronometrado/iniciar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Prática: Cronometrado"))
                .andExpect(jsonPath("$.exercises[0].timeLimitSeconds").value(60))
                .andExpect(jsonPath("$.exercises[1].timeLimitSeconds").value(120));
    }

    @Test
    @WithMockUser(username = "user")
    void iniciarSessaoModuloDeveRetornar200() throws Exception {
        when(sessaoAtividadeService.iniciarSessao(eq(1L), eq("user")))
                .thenReturn(SessaoDto.InicioResponse.builder()
                        .sessionId(1L)
                        .moduleTitle("Módulo 1")
                        .initialLives(5)
                        .totalExercises(3)
                        .exercises(List.of())
                        .build());

        mockMvc.perform(post("/api/aprender/modulos/1/iniciar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Módulo 1"));
    }

    @Test
    @WithMockUser(username = "user")
    void responderDeveRetornar200() throws Exception {
        when(sessaoAtividadeService.responder(eq(10L), any(), eq("user")))
                .thenReturn(SessaoDto.RespostaResponse.builder()
                        .correct(true)
                        .remainingLives(5)
                        .relatedTopics(List.of())
                        .build());

        mockMvc.perform(post("/api/aprender/sessoes/10/responder")
                        .contentType("application/json")
                        .content("{\"exercicioId\":1,\"resposta\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    @WithMockUser(username = "user")
    void iniciarPraticaErrosDeveRetornar200() throws Exception {
        when(sessaoAtividadeService.iniciarPratica(eq("erros"), eq("user")))
                .thenReturn(SessaoDto.InicioResponse.builder()
                        .sessionId(88L)
                        .moduleTitle("Prática: Erros")
                        .initialLives(5)
                        .totalExercises(2)
                        .exercises(List.of())
                        .build());

        mockMvc.perform(post("/api/aprender/pratica/erros/iniciar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Prática: Erros"));
    }

    @Test
    @WithMockUser(username = "user")
    void concluirDeveRetornar200() throws Exception {
        when(sessaoAtividadeService.concluir(eq(10L), eq("user")))
                .thenReturn(SessaoDto.ConclusaoResponse.builder()
                        .xpEarned(10)
                        .accuracy(100)
                        .durationSeconds(60)
                        .remainingLives(5)
                        .moduleCompleted(true)
                        .build());

        mockMvc.perform(post("/api/aprender/sessoes/10/concluir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleCompleted").value(true));
    }
}
