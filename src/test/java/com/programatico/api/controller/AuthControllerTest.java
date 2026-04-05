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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void loginDeveRetornar200QuandoRequestValido() throws Exception {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        UsuarioDto.Response usuario = UsuarioDto.Response.builder()
                .id(1L)
                .username("user")
                .email("user@email.com")
                .ativo(true)
                .build();
        UsuarioDto.LoginResponse response = new UsuarioDto.LoginResponse("token-123", usuario);

        when(usuarioService.login(any(UsuarioDto.LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-123"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.username").value("user"));
    }

    @Test
    void registroDeveRetornar400QuandoPayloadInvalido() throws Exception {
        String jsonInvalido = """
                {
                  "username": "",
                  "email": "email-invalido",
                  "senha": "123"
                }
                """;

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }
}
