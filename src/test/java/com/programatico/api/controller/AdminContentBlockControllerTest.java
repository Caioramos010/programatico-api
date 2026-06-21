package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.dto.ContentBlockDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminContentBlockService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminContentBlockController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminContentBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminContentBlockService adminContentBlockService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private ContentBlockDto.Response sampleResponse() {
        return ContentBlockDto.Response.builder()
                .id(1L)
                .moduloId(10L)
                .paginaId(5L)
                .layoutType(LayoutType.TEXT)
                .textContent("Conteúdo")
                .displayOrder(0)
                .build();
    }

    private ContentBlockDto.Request sampleRequest() {
        return ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT)
                .textContent("Conteúdo")
                .displayOrder(0)
                .build();
    }

    @Test
    void listarPorPaginaDeveRetornar200() throws Exception {
        when(adminContentBlockService.listarPorPagina(5L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/paginas/5/content-blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].layoutType").value("TEXT"));
    }

    @Test
    void criarParaPaginaDeveRetornar201() throws Exception {
        when(adminContentBlockService.criarParaPagina(eq(5L), any(ContentBlockDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/paginas/5/content-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.paginaId").value(5));
    }

    @Test
    void listarPorModuloDeveRetornar200() throws Exception {
        when(adminContentBlockService.listarPorModulo(10L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/modulos/10/content-blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].moduloId").value(10));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        when(adminContentBlockService.criar(eq(10L), any(ContentBlockDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/admin/modulos/10/content-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "textContent": "x" }
                """;

        mockMvc.perform(post("/api/admin/modulos/10/content-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        when(adminContentBlockService.atualizar(eq(1L), any(ContentBlockDto.Request.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/admin/content-blocks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        when(adminContentBlockService.atualizar(eq(99L), any(ContentBlockDto.Request.class)))
                .thenThrow(new ResourceNotFoundException("ContentBlock", 99L));

        mockMvc.perform(put("/api/admin/content-blocks/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminContentBlockService).deletar(1L);

        mockMvc.perform(delete("/api/admin/content-blocks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        doThrow(new ResourceNotFoundException("ContentBlock", 99L))
                .when(adminContentBlockService).deletar(99L);

        mockMvc.perform(delete("/api/admin/content-blocks/99"))
                .andExpect(status().isNotFound());
    }
}
