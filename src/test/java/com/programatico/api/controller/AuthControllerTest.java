package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.TrustedDeviceService;
import com.programatico.api.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    private TrustedDeviceService trustedDeviceService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void iniciarLoginDeveRetornar200QuandoRequestValido() throws Exception {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();

        when(usuarioService.iniciarLogin(any(UsuarioDto.LoginRequest.class), any()))
                .thenReturn(UsuarioDto.LoginIniciarResponse.comVerificacao("Código enviado."));

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true))
                .andExpect(jsonPath("$.mensagem").value("Código enviado."));
    }

    @Test
    void confirmarLoginDeveRetornar200QuandoRequestValido() throws Exception {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("123456")
                .build();
        UsuarioDto.Response usuario = UsuarioDto.Response.builder()
                .id(1L)
                .username("user")
                .email("user@email.com")
                .ativo(true)
                .build();
        UsuarioDto.LoginResponse response = new UsuarioDto.LoginResponse("token-123", usuario);

        when(usuarioService.confirmarLogin(any(UsuarioDto.LoginConfirmarRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-123"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.username").value("user"));
    }

    @Test
    void reenviarCodigoDeveRetornar200() throws Exception {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        when(usuarioService.iniciarLogin(any(UsuarioDto.LoginRequest.class), any()))
                .thenReturn(UsuarioDto.LoginIniciarResponse.comVerificacao("Código reenviado."));

        mockMvc.perform(post("/api/auth/login/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true));
    }

    @Test
    void solicitarRedefinicaoSenhaDeveRetornar200() throws Exception {
        when(usuarioService.solicitarRedefinicaoSenha(any()))
                .thenReturn(UsuarioDto.MessageResponse.of("E-mail enviado."));

        mockMvc.perform(post("/api/auth/redefinir-senha/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@email.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("E-mail enviado."));
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

    @Test
    void registroDeveRetornar201QuandoRequestValido() throws Exception {
        UsuarioDto.Response usuario = UsuarioDto.Response.builder()
                .id(1L)
                .username("novo")
                .email("novo@email.com")
                .ativo(false)
                .build();

        when(usuarioService.registro(any(UsuarioDto.RegistroRequest.class))).thenReturn(usuario);

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "novo",
                                  "email": "novo@email.com",
                                  "senha": "Senha@123",
                                  "idade": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("novo"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void ativarDeveRetornar200() throws Exception {
        when(usuarioService.ativar(any()))
                .thenReturn(UsuarioDto.MessageResponse.of("Conta ativada com sucesso. Faça login."));

        mockMvc.perform(post("/api/auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Conta ativada com sucesso. Faça login."));
    }

    @Test
    void solicitarAtivacaoDeveRetornar200() throws Exception {
        when(usuarioService.solicitarAtivacao(any()))
                .thenReturn(UsuarioDto.MessageResponse.of("Se o e-mail existir, você receberá um novo código de ativação."));

        mockMvc.perform(post("/api/auth/ativar/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@email.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void novaSenhaDeveRetornar200() throws Exception {
        when(usuarioService.redefinirSenha(any()))
                .thenReturn(UsuarioDto.MessageResponse.of("Senha alterada com sucesso. Faça login."));

        mockMvc.perform(post("/api/auth/redefinir-senha/nova")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codigo": "123456",
                                  "novaSenha": "NovaSenha@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Senha alterada com sucesso. Faça login."));
    }

    @Test
    void confirmarLoginDeveDefinirCookieQuandoLembrarDispositivo() throws Exception {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("123456")
                .lembrarDispositivo(true)
                .build();
        UsuarioDto.Response usuario = UsuarioDto.Response.builder()
                .id(1L)
                .username("user")
                .email("user@email.com")
                .ativo(true)
                .build();
        UsuarioDto.LoginResponse response = new UsuarioDto.LoginResponse("token-123", usuario);

        when(usuarioService.confirmarLogin(any(UsuarioDto.LoginConfirmarRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(trustedDeviceService).definirCookie(any(), eq(1L));
    }
}
