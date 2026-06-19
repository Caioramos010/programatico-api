package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void atualizarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                {
                  "email": "invalido",
                  "senha": "123"
                }
                """;

        mockMvc.perform(put("/api/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user")
    void atualizarDeveRetornar200QuandoPayloadValido() throws Exception {
        UsuarioDto.UpdateRequest request = UsuarioDto.UpdateRequest.builder()
                .username("novo-user")
                .email("novo@email.com")
                .build();
        UsuarioDto.Response response = UsuarioDto.Response.builder()
                .id(1L)
                .username("novo-user")
                .email("novo@email.com")
                .ativo(true)
                .build();
        when(usuarioService.atualizar(eq(1L), any(UsuarioDto.UpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("novo-user"))
                .andExpect(jsonPath("$.email").value("novo@email.com"));
    }
}
