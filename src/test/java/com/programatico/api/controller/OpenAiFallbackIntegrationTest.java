package com.programatico.api.controller;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.programatico.api.service.OpenAiOrganizacaoService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OpenAiFallbackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private UserProgressRepository userProgressRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private PracticeSessionRepository practiceSessionRepository;
    @Autowired private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;
    @MockitoBean private OpenAiOrganizacaoService openAiOrganizacaoService;

    private String token;
    private Modulo modulo;

    @BeforeEach
    void setUp() {
        practiceSessionExerciseRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        exerciseRepository.deleteAll();
        userProgressRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        when(openAiOrganizacaoService.organizar(any(), any(), any(), anyInt())).thenReturn(List.of());

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("openai-fallback")
                .email("openai@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userStatsRepository.save(UserStats.builder()
                .usuario(usuario)
                .totalXp(0)
                .currentLives(5)
                .currentStreak(0)
                .highestStreak(0)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Trilha")
                .description("Desc")
                .displayOrder(1)
                .build());

        modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        for (int xp : new int[]{7, 7, 7, 5, 5, 5, 3, 3, 3, 3}) {
            exerciseRepository.save(Exercise.builder()
                    .modulo(modulo)
                    .statement("Ex " + xp)
                    .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                    .exerciseData("""
                            {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                            """)
                    .xpReward(xp)
                    .build());
        }

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void iniciarSessaoDeveUsarAlgoritmoDeterministicoQuandoIaFalha() throws Exception {
        mockMvc.perform(post("/api/aprender/modulos/" + modulo.getId() + "/iniciar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExercises").value(10));

        assertEquals(10, practiceSessionExerciseRepository.findAll().size());
    }
}
