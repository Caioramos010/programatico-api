package com.programatico.api.controller;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticaFixacaoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private UserProgressRepository userProgressRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private PracticeSessionRepository practiceSessionRepository;
    @Autowired private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean
    private EmailService emailService;

    private String token;

    @BeforeEach
    void setUp() {
        practiceSessionExerciseRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        exerciseRepository.deleteAll();
        userProgressRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("fixacao-user")
                .email("fixacao@email.com")
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
                .title("Lógica Básica")
                .description("Trilha de teste")
                .displayOrder(1)
                .build());

        Modulo moduloConcluido = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo concluído")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        Modulo moduloLiberado = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo liberado")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(2)
                .description("Atividade")
                .build());

        for (int i = 1; i <= 6; i++) {
            exerciseRepository.save(Exercise.builder()
                    .modulo(moduloConcluido)
                    .statement("Exercício " + i)
                    .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                    .exerciseData("""
                            {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                            """)
                    .xpReward(3)
                    .build());
        }

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(moduloConcluido)
                .status(ProgressStatus.COMPLETED)
                .build());
        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(moduloLiberado)
                .status(ProgressStatus.UNLOCKED)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void deveIniciarPraticaFixacaoComExerciciosDeModulosConcluidos() throws Exception {
        mockMvc.perform(post("/api/aprender/pratica/fixacao/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Prática: Fixação"))
                .andExpect(jsonPath("$.totalExercises").value(5))
                .andExpect(jsonPath("$.initialLives").value(5))
                .andExpect(jsonPath("$.exercises.length()").value(5))
                .andExpect(jsonPath("$.exercises[0].statement").isNotEmpty());

        PracticeSession sessao = practiceSessionRepository.findAll().get(0);
        assertEquals(SessionType.QUICK_FIX, sessao.getSessionType());
        assertEquals(5, practiceSessionExerciseRepository.findAll().size());
    }

    @Test
    void deveRetornar400QuandoUsuarioNaoTemModulosConcluidos() throws Exception {
        userProgressRepository.deleteAll();
        userProgressRepository.save(UserProgress.builder()
                .usuario(usuarioRepository.findByUsername("fixacao-user").orElseThrow())
                .modulo(moduloRepository.findAll().get(1))
                .status(ProgressStatus.UNLOCKED)
                .build());

        mockMvc.perform(post("/api/aprender/pratica/fixacao/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Conclua um módulo antes de praticar a fixação."));
    }

    @Test
    void deveExigirAutenticacaoParaIniciarFixacao() throws Exception {
        mockMvc.perform(post("/api/aprender/pratica/fixacao/iniciar")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }
}
