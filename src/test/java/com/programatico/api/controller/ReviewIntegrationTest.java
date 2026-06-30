package com.programatico.api.controller;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserDailyMissionRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private PracticeSessionRepository practiceSessionRepository;
    @Autowired private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private UserDailyMissionRepository userDailyMissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long trackId;
    private Long secondaryTrackId;

    @BeforeEach
    void setUp() {
        userDailyMissionRepository.deleteAll();
        missionRepository.deleteAll();
        practiceSessionExerciseRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        exerciseRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("review-user")
                .email("review@email.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userStatsRepository.save(UserStats.builder()
                .usuario(usuario)
                .totalXp(250)
                .currentLives(5)
                .currentStreak(3)
                .highestStreak(5)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Logica Basica")
                .description("Trilha de review")
                .displayOrder(1)
                .build());
        trackId = track.getId();

        Track secondaryTrack = trackRepository.save(Track.builder()
                .title("Algoritmos")
                .description("Trilha secundaria")
                .displayOrder(2)
                .build());
        secondaryTrackId = secondaryTrack.getId();

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Modulo 1")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        Modulo secondaryModule = moduloRepository.save(Modulo.builder()
                .track(secondaryTrack)
                .title("Modulo 2")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade secundaria")
                .build());

        Exercise exercise = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Pergunta")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{\"options\":[{\"description\":\"A\",\"correct\":true}]}")
                .xpReward(5)
                .tags("[\"Fluxo logico\",\"Logica base\"]")
                .build());

        LocalDateTime startedAt = LocalDateTime.now()
                .minusDays(1)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        PracticeSession session = practiceSessionRepository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.QUICK_FIX)
                .startedAt(startedAt)
                .endedAt(startedAt.plusMinutes(1))
                .build());

        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(session)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(true)
                .mastered(true)
                .build());
        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(session)
                .exercise(exercise)
                .displayOrder(2)
                .isCorrect(false)
                .mastered(false)
                .build());

        Exercise secondaryExercise = exerciseRepository.save(Exercise.builder()
                .modulo(secondaryModule)
                .statement("Pergunta secundaria")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{\"options\":[{\"description\":\"A\",\"correct\":true}]}")
                .xpReward(5)
                .tags("[\"Recursao\"]")
                .build());

        PracticeSession secondarySession = practiceSessionRepository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(secondaryModule)
                .sessionType(SessionType.TIMED)
                .startedAt(startedAt)
                .endedAt(startedAt.plusSeconds(45))
                .build());

        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(secondarySession)
                .exercise(secondaryExercise)
                .displayOrder(1)
                .isCorrect(true)
                .mastered(true)
                .build());

        Mission mission = missionRepository.save(Mission.builder()
                .title("Treinar logica")
                .objectiveType("practice")
                .xpReward(20)
                .quantidade(3)
                .build());

        userDailyMissionRepository.save(UserDailyMission.builder()
                .usuario(usuario)
                .mission(mission)
                .missionDate(LocalDate.now())
                .currentProgress(2)
                .goal(3)
                .completed(false)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void deveRetornarPayloadAgregadoDaReview() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", String.valueOf(trackId))
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedTrackId").value(trackId))
                .andExpect(jsonPath("$.selectedDays").value(7))
                .andExpect(jsonPath("$.currentXp").value(250))
                .andExpect(jsonPath("$.availableTracks.length()").value(2))
                .andExpect(jsonPath("$.stats[0].title").value("Exercicios feitos"))
                .andExpect(jsonPath("$.stats[0].value").value("2"))
                .andExpect(jsonPath("$.stats[1].value").value("50%"))
                .andExpect(jsonPath("$.stats[3].value").value("30s"))
                .andExpect(jsonPath("$.performanceData.length()").value(7))
                .andExpect(jsonPath("$.subjectAccuracy[0].assunto").value("Fluxo logico"))
                .andExpect(jsonPath("$.errorsBySubject[0].assunto").value("Fluxo logico"))
                .andExpect(jsonPath("$.reviewNow[0].assunto").value("Fluxo logico"))
                .andExpect(jsonPath("$.recentMissions[0].label").value("Treinar logica"))
                .andExpect(jsonPath("$.recentMissions[0].status").value("Em progresso (2/3)"));
    }

    @Test
    void deveExigirAutenticacaoParaConsultarReview() throws Exception {
        mockMvc.perform(get("/api/review"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void deveRetornar400QuandoTrackIdForInvalido() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", "999999")
                        .param("days", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Trilha informada nao encontrada."));
    }

    @Test
    void deveUsarSeteDiasQuandoDaysForInvalido() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", String.valueOf(trackId))
                        .param("days", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDays").value(7))
                .andExpect(jsonPath("$.performanceData.length()").value(7));
    }

    @Test
    void deveConsiderarApenasSessoesDaTrilhaSelecionada() throws Exception {
        mockMvc.perform(get("/api/review")
                        .header("Authorization", "Bearer " + token)
                        .param("trackId", String.valueOf(secondaryTrackId))
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedTrackId").value(secondaryTrackId))
                .andExpect(jsonPath("$.stats[0].value").value("1"))
                .andExpect(jsonPath("$.stats[1].value").value("100%"))
                .andExpect(jsonPath("$.stats[3].value").value("45s"))
                .andExpect(jsonPath("$.subjectAccuracy[0].assunto").value("Recursao"))
                .andExpect(jsonPath("$.errorsBySubject").isEmpty())
                .andExpect(jsonPath("$.reviewNow[0].assunto").value("Recursao"));
    }
}
