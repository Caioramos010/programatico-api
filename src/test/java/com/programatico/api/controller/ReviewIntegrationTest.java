package com.programatico.api.controller;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserStatsRepository;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private PracticeSessionRepository practiceSessionRepository;
    @Autowired private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long trackId;

    @BeforeEach
    void setUp() {
        practiceSessionExerciseRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        exerciseRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("review-user")
                .email("review@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userStatsRepository.save(UserStats.builder()
                .usuario(usuario)
                .totalXp(120)
                .currentLives(5)
                .currentStreak(2)
                .highestStreak(3)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Trilha review")
                .description("Desc")
                .displayOrder(1)
                .build());
        trackId = track.getId();

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo review")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        Exercise ex1 = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Q1")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("""
                        {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                        """)
                .xpReward(3)
                .tags("[\"logica\"]")
                .build());

        Exercise ex2 = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Q2")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("""
                        {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                        """)
                .xpReward(3)
                .tags("[\"logica\"]")
                .build());

        LocalDateTime inicio = LocalDateTime.now().minusMinutes(5);
        PracticeSession sessao = practiceSessionRepository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(inicio)
                .endedAt(inicio.plusMinutes(2))
                .build());

        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(ex1)
                .displayOrder(1)
                .isCorrect(true)
                .userAnswer("A")
                .build());
        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(ex2)
                .displayOrder(2)
                .isCorrect(false)
                .userAnswer("B")
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void getReviewDeveRetornarEstatisticasDoPeriodo() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", String.valueOf(trackId))
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedTrackId").value(trackId))
                .andExpect(jsonPath("$.selectedDays").value(7))
                .andExpect(jsonPath("$.currentXp").value(120))
                .andExpect(jsonPath("$.stats[0].value").value("2"))
                .andExpect(jsonPath("$.stats[1].value").value("50%"))
                .andExpect(jsonPath("$.availableTracks[0].title").value("Trilha review"));
    }

    @Test
    void getReviewDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/review"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void getReviewComTrilhaInvalidaDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Trilha informada nao encontrada."));
    }
}
