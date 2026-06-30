package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.service.EmailService;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthExtraFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;

    private static final String SENHA = "Senha@123";

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);
        doNothing().when(emailService).enviarCodigoAtivacao(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doNothing().when(emailService).enviarCodigoVerificacaoLogin(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doNothing().when(emailService).enviarCodigoRedefinicaoSenha(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reenviarCodigoLoginDeveReutilizarFluxoDeIniciarLogin() throws Exception {
        registrarEAtivar("reenviar@test.com", "reenviar-user");

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("reenviar@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true));

        Usuario usuario = usuarioRepository.findByEmail("reenviar@test.com").orElseThrow();
        String codigoAnterior = usuario.getCodigoVerificacaoLogin();

        mockMvc.perform(post("/api/auth/login/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("reenviar@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true));

        Usuario atualizado = usuarioRepository.findByEmail("reenviar@test.com").orElseThrow();
        assertNotNull(atualizado.getCodigoVerificacaoLogin());
        assertNotNull(codigoAnterior);
    }

    @Test
    void solicitarAtivacaoDeveEnviarNovoCodigo() throws Exception {
        String registroJson = """
                {
                  "username": "pendente-user",
                  "email": "pendente@test.com",
                  "senha": "%s",
                  "idade": 20
                }
                """.formatted(SENHA);

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/ativar/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pendente@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        assertNotNull(usuarioRepository.findByEmail("pendente@test.com").orElseThrow().getCodigoAtivacao());
    }

    @Test
    void redefinirSenhaPontaAPontaDeveAlterarSenha() throws Exception {
        registrarEAtivar("reset@test.com", "reset-user");

        mockMvc.perform(post("/api/auth/redefinir-senha/solicitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        Usuario usuario = usuarioRepository.findByEmail("reset@test.com").orElseThrow();
        assertNotNull(usuario.getCodigoRedefinicaoSenha());

        String novaSenhaJson = """
                {
                  "codigo": "%s",
                  "novaSenha": "NovaSenha@456"
                }
                """.formatted(usuario.getCodigoRedefinicaoSenha());

        mockMvc.perform(post("/api/auth/redefinir-senha/nova")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novaSenhaJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "emailOuUsername": "reset@test.com",
                                  "senha": "NovaSenha@456"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void registrarEAtivar(String email, String username) throws Exception {
        String registroJson = """
                {
                  "username": "%s",
                  "email": "%s",
                  "senha": "%s",
                  "idade": 20
                }
                """.formatted(username, email, SENHA);

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson))
                .andExpect(status().isCreated());

        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(post("/api/auth/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + usuario.getCodigoAtivacao() + "\"}"))
                .andExpect(status().isOk());
    }

    private String loginJson(String email) {
        return """
                {
                  "emailOuUsername": "%s",
                  "senha": "%s"
                }
                """.formatted(email, SENHA);
    }
}
