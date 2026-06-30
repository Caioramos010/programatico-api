package com.programatico.api.controller;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UserStatsRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearnIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long moduloId;

    @BeforeEach
    void setUp() {
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("learn-user")
                .email("learn@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userStatsRepository.save(UserStats.builder()
                .usuario(usuario)
                .totalXp(50)
                .currentLives(5)
                .currentStreak(1)
                .highestStreak(2)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Trilha integração")
                .description("Desc")
                .displayOrder(1)
                .build());

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo teórico")
                .moduleType(ModuleType.STUDY)
                .displayOrder(1)
                .description("Estudo")
                .build());
        moduloId = modulo.getId();

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void trilhaStatsEMissoesDevemRetornar200() throws Exception {
        mockMvc.perform(get("/api/aprender/trilha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Trilha integração"));

        mockMvc.perform(get("/api/aprender/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").value(50));

        mockMvc.perform(get("/api/aprender/missoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void concluirTeoricoDeveRetornar204() throws Exception {
        mockMvc.perform(get("/api/aprender/modulos/" + moduloId + "/teorico")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Módulo teórico"));

        mockMvc.perform(post("/api/aprender/modulos/" + moduloId + "/teorico/concluir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
