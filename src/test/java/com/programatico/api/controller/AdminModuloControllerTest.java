package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.ModuloDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminModuloService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminModuloController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminModuloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminModuloService adminModuloService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private ModuloDto.Response sampleResponse() {
        return ModuloDto.Response.builder()
                .id(1L)
                .trackId(7L)
                .title("Variáveis")
                .moduleType(ModuleType.STUDY)
                .displayOrder(0)
                .description("Desc")
                .totalComponents(2L)
                .totalXp(20L)
                .build();
    }

    private ModuloDto.Request sampleRequest() {
        return ModuloDto.Request.builder()
                .title("Variáveis")
                .moduleType(ModuleType.STUDY)
                .description("Desc")
                .build();
    }

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminModuloService.listarPorTrilha(7L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/trilhas/7/modulos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].moduleType").value("STUDY"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        when(adminModuloService.criar(eq(7L), any(ModuloDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/trilhas/7/modulos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.trackId").value(7));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "title": "", "description": "x" }
                """;

        mockMvc.perform(post("/api/admin/trilhas/7/modulos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        when(adminModuloService.atualizar(eq(1L), any(ModuloDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/modulos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Variáveis"));
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        when(adminModuloService.atualizar(eq(99L), any(ModuloDto.Request.class)))
                .thenThrow(new ResourceNotFoundException("Modulo", 99L));

        mockMvc.perform(put("/api/admin/modulos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void reordenarDeveRetornar204() throws Exception {
        doNothing().when(adminModuloService).reordenar(eq(7L), anyList());

        String body = """
                { "ids": [3, 1, 2] }
                """;

        mockMvc.perform(put("/api/admin/trilhas/7/modulos/reordenar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminModuloService).deletar(1L);

        mockMvc.perform(delete("/api/admin/modulos/1"))
                .andExpect(status().isNoContent());
    }
}
