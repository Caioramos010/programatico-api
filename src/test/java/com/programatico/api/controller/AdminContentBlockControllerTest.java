package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.dto.ContentBlockDto;
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
import static org.mockito.Mockito.doNothing;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AdminContentBlockService adminContentBlockService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarPorModuloDeveRetornar200() throws Exception {
        when(adminContentBlockService.listarPorModulo(1L)).thenReturn(List.of(
                ContentBlockDto.Response.builder().id(1L).layoutType(LayoutType.TEXT).textContent("T").build()));

        mockMvc.perform(get("/api/admin/modulos/1/content-blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].textContent").value("T"));
    }

    @Test
    void listarPorPaginaDeveRetornar200() throws Exception {
        when(adminContentBlockService.listarPorPagina(5L)).thenReturn(List.of(
                ContentBlockDto.Response.builder().id(3L).layoutType(LayoutType.TEXT).textContent("Página").build()));

        mockMvc.perform(get("/api/admin/paginas/5/content-blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].textContent").value("Página"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        ContentBlockDto.Request request = ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT).textContent("Bloco").displayOrder(1).build();
        when(adminContentBlockService.criar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(ContentBlockDto.Response.builder().id(2L).textContent("Bloco").build());

        mockMvc.perform(post("/api/admin/modulos/1/content-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textContent").value("Bloco"));
    }

    @Test
    void criarParaPaginaDeveRetornar201() throws Exception {
        ContentBlockDto.Request request = ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT).textContent("Bloco pag").displayOrder(1).build();
        when(adminContentBlockService.criarParaPagina(org.mockito.ArgumentMatchers.eq(5L), any()))
                .thenReturn(ContentBlockDto.Response.builder().id(4L).textContent("Bloco pag").build());

        mockMvc.perform(post("/api/admin/paginas/5/content-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textContent").value("Bloco pag"));
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        ContentBlockDto.Request request = ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT).textContent("Atualizado").displayOrder(1).build();
        when(adminContentBlockService.atualizar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(ContentBlockDto.Response.builder().id(1L).textContent("Atualizado").build());

        mockMvc.perform(put("/api/admin/content-blocks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textContent").value("Atualizado"));
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminContentBlockService).deletar(1L);
        mockMvc.perform(delete("/api/admin/content-blocks/1")).andExpect(status().isNoContent());
    }
}
