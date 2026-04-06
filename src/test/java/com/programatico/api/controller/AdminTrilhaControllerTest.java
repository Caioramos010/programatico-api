package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.TrilhaDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminTrilhaService;
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

@WebMvcTest(AdminTrilhaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminTrilhaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminTrilhaService adminTrilhaService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarDeveRetornar200ComListaDeTrilhas() throws Exception {
        TrilhaDto.Response trilha = TrilhaDto.Response.builder()
                .id(1L).title("Lógica Básica").description("Desc")
                .displayOrder(1).totalModulos(3L).build();

        when(adminTrilhaService.listarTodas()).thenReturn(List.of(trilha));

        mockMvc.perform(get("/api/admin/trilhas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Lógica Básica"))
                .andExpect(jsonPath("$[0].totalModulos").value(3));
    }

    @Test
    void criarDeveRetornar201ComTrilhaCriada() throws Exception {
        TrilhaDto.Request request = TrilhaDto.Request.builder()
                .title("Nova Trilha").description("Desc").build();
        TrilhaDto.Response response = TrilhaDto.Response.builder()
                .id(2L).title("Nova Trilha").description("Desc")
                .displayOrder(2).totalModulos(0L).build();

        when(adminTrilhaService.criar(any(TrilhaDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/trilhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.title").value("Nova Trilha"));
    }

    @Test
    void criarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "title": "", "description": "" }
                """;

        mockMvc.perform(post("/api/admin/trilhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar200ComTrilhaAtualizada() throws Exception {
        TrilhaDto.Request request = TrilhaDto.Request.builder()
                .title("Atualizada").description("Nova desc").build();
        TrilhaDto.Response response = TrilhaDto.Response.builder()
                .id(1L).title("Atualizada").description("Nova desc")
                .displayOrder(1).totalModulos(2L).build();

        when(adminTrilhaService.atualizar(eq(1L), any(TrilhaDto.Request.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/trilhas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Atualizada"));
    }

    @Test
    void deletarDeveRetornar204QuandoTrilhaDeletada() throws Exception {
        doNothing().when(adminTrilhaService).deletar(1L);

        mockMvc.perform(delete("/api/admin/trilhas/1"))
                .andExpect(status().isNoContent());
    }
}
