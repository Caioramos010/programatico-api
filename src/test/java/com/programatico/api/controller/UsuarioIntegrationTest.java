package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NivelHabilidade;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long usuarioId;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("profile-user")
                .email("profile@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(22)
                .ativo(true)
                .role(TipoUsuario.USER)
                .nivelHabilidade(NivelHabilidade.BEGINNER)
                .build());
        usuarioId = usuario.getId();
        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void atualizarPerfilDevePersistirAlteracoes() throws Exception {
        mockMvc.perform(put("/api/usuarios/" + usuarioId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"profile-updated",
                                  "email":"updated@test.com",
                                  "idade":23
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profile-updated"))
                .andExpect(jsonPath("$.email").value("updated@test.com"))
                .andExpect(jsonPath("$.idade").value(23));

        Usuario atualizado = usuarioRepository.findById(usuarioId).orElseThrow();
        assertEquals("profile-updated", atualizado.getUsername());
        assertEquals("updated@test.com", atualizado.getEmail());
        assertEquals(23, atualizado.getIdade());
    }
}
