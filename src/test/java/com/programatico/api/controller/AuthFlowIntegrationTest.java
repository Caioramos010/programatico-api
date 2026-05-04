package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
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

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setup() {
        usuarioRepository.deleteAll();
        doNothing().when(emailService).enviarCodigoAtivacao(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doNothing().when(emailService).enviarCodigoRedefinicaoSenha(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doNothing().when(emailService).enviarCodigoExclusaoConta(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doNothing().when(emailService).enviarCodigoVerificacaoLogin(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
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

        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        assertNotNull(usuario.getCodigoAtivacao());
        assertTrue(usuario.getCodigoAtivacao().length() >= 6);

        String ativacaoJson = """
                {
                  "codigo": "%s"
                }
                """.formatted(usuario.getCodigoAtivacao());

        mockMvc.perform(post("/api/auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ativacaoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

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
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        Usuario usuarioAtivo = usuarioRepository.findByEmail(email).orElseThrow();
        assertNotNull(usuarioAtivo.getCodigoVerificacaoLogin());

        String loginConfirmarJson = """
                {
                  "emailOuUsername": "%s",
                  "senha": "%s",
                  "codigo": "%s"
                }
                """.formatted(email, senha, usuarioAtivo.getCodigoVerificacaoLogin());

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
