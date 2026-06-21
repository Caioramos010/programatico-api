package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.TeoriaPaginaDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminTeoriaPaginaService;
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

@WebMvcTest(AdminTeoriaPaginaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminTeoriaPaginaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminTeoriaPaginaService adminTeoriaPaginaService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private TeoriaPaginaDto.Response sampleResponse() {
        return TeoriaPaginaDto.Response.builder()
                .id(1L)
                .moduloId(10L)
                .title("Introdução")
                .description("Desc")
                .displayOrder(0)
                .totalBlocos(2L)
                .build();
    }

    private TeoriaPaginaDto.Request sampleRequest() {
        return TeoriaPaginaDto.Request.builder()
                .title("Introdução")
                .description("Desc")
                .build();
    }

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminTeoriaPaginaService.listar(10L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/modulos/10/paginas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Introdução"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        when(adminTeoriaPaginaService.criar(eq(10L), any(TeoriaPaginaDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/modulos/10/paginas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.moduloId").value(10));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "title": "", "description": "x" }
                """;

        mockMvc.perform(post("/api/admin/modulos/10/paginas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        when(adminTeoriaPaginaService.atualizar(eq(1L), any(TeoriaPaginaDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/paginas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Introdução"));
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        when(adminTeoriaPaginaService.atualizar(eq(99L), any(TeoriaPaginaDto.Request.class)))
                .thenThrow(new ResourceNotFoundException("TeoriaPagina", 99L));

        mockMvc.perform(put("/api/admin/paginas/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminTeoriaPaginaService).deletar(1L);

        mockMvc.perform(delete("/api/admin/paginas/1"))
                .andExpect(status().isNoContent());
    }
}
