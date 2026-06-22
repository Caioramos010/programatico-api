package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integração que prova a correção do IDOR: um usuário autenticado só acessa/altera o próprio
 * cadastro em /api/usuarios/{id}; tentar o id de outro responde 404 (sem revelar existência).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioIdorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmailService emailService;

    private Usuario alice;
    private Usuario bob;
    private String tokenAlice;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        alice = usuarioRepository.save(Usuario.builder()
                .username("alice").email("alice@email.com")
                .senha(passwordEncoder.encode("Senha@123")).idade(20)
                .ativo(true).role(TipoUsuario.USER).build());
        bob = usuarioRepository.save(Usuario.builder()
                .username("bob").email("bob@email.com")
                .senha(passwordEncoder.encode("Senha@123")).idade(22)
                .ativo(true).role(TipoUsuario.USER).build());
        tokenAlice = jwtUtil.gerarToken(alice.getUsername(), alice.getId());
    }

    @Test
    void usuarioAcessaProprioCadastro() throws Exception {
        mockMvc.perform(get("/api/usuarios/" + alice.getId())
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void usuarioNaoAcessaCadastroDeOutro() throws Exception {
        mockMvc.perform(get("/api/usuarios/" + bob.getId())
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioNaoAtualizaCadastroDeOutro() throws Exception {
        UsuarioDto.UpdateRequest request = UsuarioDto.UpdateRequest.builder()
                .username("invadido")
                .build();

        mockMvc.perform(put("/api/usuarios/" + bob.getId())
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        assertEquals("bob", usuarioRepository.findById(bob.getId()).orElseThrow().getUsername());
    }
}
