package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCrudIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private TeoriaPaginaRepository teoriaPaginaRepository;
    @Autowired private ContentBlockRepository contentBlockRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String adminToken;
    private Long userId;

    @BeforeEach
    void setUp() {
        contentBlockRepository.deleteAll();
        teoriaPaginaRepository.deleteAll();
        exerciseRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        missionRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-crud")
                .email("admin-crud@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(30)
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build());

        Usuario user = usuarioRepository.save(Usuario.builder()
                .username("user-crud")
                .email("user-crud@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
        userId = user.getId();

        adminToken = jwtUtil.gerarToken(admin.getUsername(), admin.getId());
    }

    @Test
    void fluxoAdminCrudCompleto() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());

        MvcResult trilhaResult = mockMvc.perform(post("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Trilha E2E","description":"Integração"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Trilha E2E"))
                .andReturn();

        long trilhaId = objectMapper.readTree(trilhaResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult moduloResult = mockMvc.perform(post("/api/admin/trilhas/" + trilhaId + "/modulos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Módulo E2E","moduleType":"ACTIVITY","description":"Atividade"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Módulo E2E"))
                .andReturn();

        long moduloId = objectMapper.readTree(moduloResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult exerciseResult = mockMvc.perform(post("/api/admin/modulos/" + moduloId + "/exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statement": "Pergunta?",
                                  "exerciseType": "MULTIPLE_CHOICE",
                                  "exerciseData": "{\\"opcoes\\":[\\"A\\",\\"B\\"]}",
                                  "xpReward": 5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statement").value("Pergunta?"))
                .andReturn();

        long exerciseId = objectMapper.readTree(exerciseResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult paginaResult = mockMvc.perform(post("/api/admin/modulos/" + moduloId + "/paginas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Página 1","description":"Intro"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Página 1"))
                .andReturn();

        long paginaId = objectMapper.readTree(paginaResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/admin/paginas/" + paginaId + "/content-blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layoutType":"TEXT","textContent":"Bloco teoria","displayOrder":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textContent").value("Bloco teoria"));

        mockMvc.perform(get("/api/admin/paginas/" + paginaId + "/content-blocks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].textContent").value("Bloco teoria"));

        MvcResult missaoResult = mockMvc.perform(post("/api/admin/missoes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Missão E2E","objectiveType":"XP","xpReward":10,"quantity":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Missão E2E"))
                .andReturn();

        long missaoId = objectMapper.readTree(missaoResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='user-crud')]").exists());

        mockMvc.perform(put("/api/admin/usuarios/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"USER","ativo":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(get("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Trilha E2E"));

        mockMvc.perform(delete("/api/admin/exercises/" + exerciseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/missoes/" + missaoId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/trilhas/" + trilhaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
