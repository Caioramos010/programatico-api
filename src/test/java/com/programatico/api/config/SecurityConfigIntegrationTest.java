package com.programatico.api.config;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario user = usuarioRepository.save(Usuario.builder()
                .username("sec-user")
                .email("sec-user@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("sec-admin")
                .email("sec-admin@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(30)
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build());

        userToken = jwtUtil.gerarToken(user.getUsername(), user.getId());
        adminToken = jwtUtil.gerarToken(admin.getUsername(), admin.getId());
    }
    @Test
    void endpointAuthDeveSerPublicoMesmoSemToken() throws Exception {
        mockMvc.perform(post("/api/auth/login/iniciar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointUsuariosDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void endpointAdminDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/admin/trilhas"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void webhookAbacatePayDeveSerPublico() throws Exception {
        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status != 401 && status != 403);
                });
    }

    @Test
    void usuarioAutenticadoDeveAcessarReview() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumNaoDeveAcessarAdminComJwt() throws Exception {
        mockMvc.perform(get("/api/admin/missoes")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 403 || status == 401);
                });
    }

    @Test
    void adminComJwtDeveAcessarMissoes() throws Exception {
        mockMvc.perform(get("/api/admin/missoes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
