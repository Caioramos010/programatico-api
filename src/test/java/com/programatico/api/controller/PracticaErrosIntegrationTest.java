package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticaErrosIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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

    private String token;
    private Usuario usuario;

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

        usuario = usuarioRepository.save(Usuario.builder()
                .username("erros-user")
                .email("erros@test.com")
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

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        Exercise exercise = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Questão errada")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("""
                        {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                        """)
                .xpReward(3)
                .build());

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.COMPLETED)
                .build());

        PracticeSession sessaoAnterior = practiceSessionRepository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now().minusDays(1))
                .endedAt(LocalDateTime.now().minusDays(1).plusMinutes(5))
                .build());

        practiceSessionExerciseRepository.save(PracticeSessionExercise.builder()
                .practiceSession(sessaoAnterior)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(false)
                .userAnswer("B")
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void deveIniciarPraticaErrosComExerciciosErrados() throws Exception {
        mockMvc.perform(post("/api/aprender/pratica/erros/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleTitle").value("Prática: Erros"))
                .andExpect(jsonPath("$.totalExercises").value(1));
    }

    @Test
    void fluxoErrosResponderConcluirDeveCompletar() throws Exception {
        var inicio = mockMvc.perform(post("/api/aprender/pratica/erros/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(inicio.getResponse().getContentAsString());
        long sessionId = body.get("sessionId").asLong();
        long exercicioId = body.get("exercises").get(0).get("id").asLong();

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"A"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/concluir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accuracy").value(100));
    }

    @Test
    void deveRetornar400QuandoNaoHaErros() throws Exception {
        practiceSessionExerciseRepository.deleteAll();

        mockMvc.perform(post("/api/aprender/pratica/erros/iniciar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Você ainda não tem erros para praticar."));
    }
}
