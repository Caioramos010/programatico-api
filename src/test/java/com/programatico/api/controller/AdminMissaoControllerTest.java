package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.MissaoDto;
import com.programatico.api.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMissaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminMissaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminMissaoService adminMissaoService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private MissaoDto.Response sampleResponse() {
        return MissaoDto.Response.builder()
                .id(1L)
                .title("Complete 3 módulos")
                .objectiveType("COMPLETE_MODULES")
                .xpReward(50)
                .quantity(3)
                .build();
    }

    private MissaoDto.Request sampleRequest() {
        return MissaoDto.Request.builder()
                .title("Complete 3 módulos")
                .objectiveType("COMPLETE_MODULES")
                .xpReward(50)
                .quantity(3)
                .build();
    }

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminMissaoService.listarTodas()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/missoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Complete 3 módulos"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        when(adminMissaoService.criar(any(MissaoDto.Request.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/missoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.xpReward").value(50));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "title": "", "objectiveType": "", "xpReward": 0, "quantity": 0 }
                """;

        mockMvc.perform(post("/api/admin/missoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        when(adminMissaoService.atualizar(eq(1L), any(MissaoDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/missoes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Complete 3 módulos"));
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        when(adminMissaoService.atualizar(eq(99L), any(MissaoDto.Request.class)))
                .thenThrow(new ResourceNotFoundException("Mission", 99L));

        mockMvc.perform(put("/api/admin/missoes/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminMissaoService).deletar(1L);

        mockMvc.perform(delete("/api/admin/missoes/1"))
                .andExpect(status().isNoContent());
    }
}
