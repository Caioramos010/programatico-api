package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.AdminUsuarioDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminUsuarioService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUsuarioService adminUsuarioService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    private AdminUsuarioDto.Response sampleResponse() {
        return AdminUsuarioDto.Response.builder()
                .id(1L)
                .username("user")
                .email("user@email.com")
                .role(TipoUsuario.USER)
                .ativo(true)
                .build();
    }

    @Test
    void listarSemBuscaDeveRetornar200() throws Exception {
        when(adminUsuarioService.listarTodos(isNull())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("user"));
    }

    @Test
    void listarComBuscaDeveRetornar200() throws Exception {
        when(adminUsuarioService.listarTodos("ana")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/admin/usuarios").param("busca", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("user@email.com"));
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        AdminUsuarioDto.UpdateRequest request = AdminUsuarioDto.UpdateRequest.builder()
                .role(TipoUsuario.ADMIN)
                .ativo(false)
                .build();
        AdminUsuarioDto.Response response = AdminUsuarioDto.Response.builder()
                .id(1L)
                .username("user")
                .email("user@email.com")
                .role(TipoUsuario.ADMIN)
                .ativo(false)
                .build();
        when(adminUsuarioService.atualizar(eq(1L), any(AdminUsuarioDto.UpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/admin/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void atualizarDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                { "role": null, "ativo": null }
                """;

        mockMvc.perform(put("/api/admin/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarDeveRetornar404QuandoNaoEncontrado() throws Exception {
        AdminUsuarioDto.UpdateRequest request = AdminUsuarioDto.UpdateRequest.builder()
                .role(TipoUsuario.USER)
                .ativo(true)
                .build();
        when(adminUsuarioService.atualizar(eq(99L), any(AdminUsuarioDto.UpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Usuario", 99L));

        mockMvc.perform(put("/api/admin/usuarios/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deletarDeveRetornar204() throws Exception {
        doNothing().when(adminUsuarioService).deletar(1L);

        mockMvc.perform(delete("/api/admin/usuarios/1"))
                .andExpect(status().isNoContent());
    }
}
