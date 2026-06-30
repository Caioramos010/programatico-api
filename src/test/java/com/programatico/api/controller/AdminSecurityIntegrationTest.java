package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-test")
                .email("admin-test@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(30)
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build());

        Usuario user = usuarioRepository.save(Usuario.builder()
                .username("user-test")
                .email("user-test@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        adminToken = jwtUtil.gerarToken(admin.getUsername(), admin.getId());
        userToken = jwtUtil.gerarToken(user.getUsername(), user.getId());
    }

    @Test
    void adminComJwtDeveAcessarRotasAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumNaoDeveAcessarRotasAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 401);
                });
    }

    @Test
    void adminDeveAcessarDashboardEUsuarios() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumNaoDeveAcessarDashboardAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 401);
                });

        mockMvc.perform(put("/api/admin/usuarios/1")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 401 || status == 400);
                });
    }
}
