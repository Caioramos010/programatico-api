package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
import com.programatico.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setup() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);
        doNothing().when(emailService).enviarCodigoAtivacao(anyString(), anyString(), anyString());
        doNothing().when(emailService).enviarCodigoRedefinicaoSenha(anyString(), anyString(), anyString());
        doNothing().when(emailService).enviarCodigoExclusaoConta(anyString(), anyString(), anyString());
        doNothing().when(emailService).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
    }

    @Test
    void deveRegistrarAtivarELogarComSucesso() throws Exception {
        String email = "fluxo@email.com";
        String username = "fluxo-user";
        String senha = "Senha@123";

        String registroJson = """
                {
                  "username": "%s",
                  "email": "%s",
                  "senha": "%s",
                  "idade": 21
                }
                """.formatted(username, email, senha);

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.ativo").value(false));

        // O código chega pelo e-mail (capturado do mock); o banco guarda só o hash.
        ArgumentCaptor<String> codigoAtivacao = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoAtivacao(eq(email), anyString(), codigoAtivacao.capture());
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        assertNotNull(usuario.getCodigoAtivacao());
        assertNotEquals(codigoAtivacao.getValue(), usuario.getCodigoAtivacao());

        String ativacaoJson = """
                {
                  "codigo": "%s"
                }
                """.formatted(codigoAtivacao.getValue());

        mockMvc.perform(post("/api/auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ativacaoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        String loginIniciarJson = """
                {
                  "emailOuUsername": "%s",
                  "senha": "%s"
                }
                """.formatted(email, senha);

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginIniciarJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        ArgumentCaptor<String> codigoLogin = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacaoLogin(eq(email), anyString(), codigoLogin.capture());

        String loginConfirmarJson = """
                {
                  "emailOuUsername": "%s",
                  "senha": "%s",
                  "codigo": "%s"
                }
                """.formatted(email, senha, codigoLogin.getValue());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginConfirmarJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        assertNotNull(body.get("token").asText());
    }
}
