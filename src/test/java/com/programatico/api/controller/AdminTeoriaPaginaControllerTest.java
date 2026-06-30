package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.TeoriaPaginaDto;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AdminTeoriaPaginaService adminTeoriaPaginaService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarDeveRetornar200() throws Exception {
        when(adminTeoriaPaginaService.listar(1L)).thenReturn(List.of(
                TeoriaPaginaDto.Response.builder().id(1L).title("Página 1").totalBlocos(2).build()));

        mockMvc.perform(get("/api/admin/modulos/1/paginas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Página 1"));
    }

    @Test
    void criarDeveRetornar201() throws Exception {
        TeoriaPaginaDto.Request request = TeoriaPaginaDto.Request.builder()
                .title("Nova página").description("Desc").build();
        when(adminTeoriaPaginaService.criar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(TeoriaPaginaDto.Response.builder().id(2L).title("Nova página").build());

        mockMvc.perform(post("/api/admin/modulos/1/paginas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nova página"));
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        TeoriaPaginaDto.Request request = TeoriaPaginaDto.Request.builder()
                .title("Atualizada").description("Desc").build();
        when(adminTeoriaPaginaService.atualizar(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(TeoriaPaginaDto.Response.builder().id(1L).title("Atualizada").build());

        mockMvc.perform(put("/api/admin/paginas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Atualizada"));
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminTeoriaPaginaService).deletar(1L);
        mockMvc.perform(delete("/api/admin/paginas/1")).andExpect(status().isNoContent());
    }
}
