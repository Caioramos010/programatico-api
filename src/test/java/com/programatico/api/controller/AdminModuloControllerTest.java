package com.programatico.api.controller;

import com.programatico.api.dto.ModuloDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminModuloService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminModuloController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminModuloControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminModuloService adminModuloService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void listarDeveRetornar200() throws Exception {
        ModuloDto.Response modulo = ModuloDto.Response.builder().id(1L).title("M1").build();
        when(adminModuloService.listarPorTrilha(1L)).thenReturn(List.of(modulo));

        mockMvc.perform(get("/api/admin/trilhas/1/modulos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("M1"));
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminModuloService).deletar(1L);
        mockMvc.perform(delete("/api/admin/modulos/1")).andExpect(status().isNoContent());
    }
}
