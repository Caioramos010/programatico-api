package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.domain.Notification;
import com.programatico.api.repository.NotificationRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessaoAtividadeEdgeCasesIntegrationTest {

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
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Modulo modulo;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        practiceSessionExerciseRepository.deleteAll();
        practiceSessionRepository.deleteAll();
        notificationRepository.deleteAll();
        exerciseRepository.deleteAll();
        userProgressRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        userStatsRepository.deleteAll();
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        usuario = usuarioRepository.save(Usuario.builder()
                .username("edge-user")
                .email("edge@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        userStatsRepository.save(UserStats.builder()
                .usuario(usuario)
                .totalXp(10)
                .currentLives(5)
                .currentStreak(0)
                .highestStreak(0)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Trilha edge")
                .description("Desc")
                .displayOrder(1)
                .build());

        modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo edge")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void replayModuloConcluidoNaoDeveGanharXp() throws Exception {
        salvarExercicio("Q1", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.COMPLETED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"A"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/concluir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpEarned").value(0));

        assertEquals(10, userStatsRepository.findByUsuario(usuario).orElseThrow().getTotalXp());
    }

    @Test
    void responderDuasVezesMesmoExercicioDeveRetornar400() throws Exception {
        salvarExercicio("Q1", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);
        String payload = """
                {"exercicioId":%d,"resposta":"A"}
                """.formatted(exercicioId);

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Este exercício já foi respondido."));
    }

    @Test
    void acertoAbaixoDe50PorcentoNaoDeveConcluirModulo() throws Exception {
        salvarExercicio("Q1", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);
        salvarExercicio("Q2", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);
        salvarExercicio("Q3", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"B"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false));

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/concluir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accuracy").value(0))
                .andExpect(jsonPath("$.moduleCompleted").value(false));
    }

    @Test
    void pairsRespostaParcialNaoMarcaComoRespondido() throws Exception {
        salvarExercicio("Pares", ExerciseType.PAIRS, """
                {"pairs":[{"left":"7","right":"inteiro"},{"left":"2.5","right":"decimal"}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"[{\\"left\\":\\"7\\",\\"right\\":\\"inteiro\\"}]"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"[{\\"left\\":\\"7\\",\\"right\\":\\"inteiro\\"},{\\"left\\":\\"2.5\\",\\"right\\":\\"decimal\\"}]"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void dragDropRespostaCorretaDeveValidarOrdem() throws Exception {
        salvarExercicio("Ordene", ExerciseType.DRAG_DROP, """
                {"items":["primeiro","segundo"]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);
        String resposta = objectMapper.writeValueAsString(java.util.List.of("primeiro", "segundo"));
        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("exercicioId", exercicioId, "resposta", resposta));

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void usuarioRootNaoDevePerderVidasAoErrar() throws Exception {
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        usuarioRepository.save(usuario);

        salvarExercicio("Q1", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();
        long exercicioId = primeiroExercicioId(sessionId);

        mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exercicioId":%d,"resposta":"B"}
                                """.formatted(exercicioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.remainingLives").value(5));
    }

    @Test
    void zerarVidasDeveCriarNotificacaoSemVidas() throws Exception {
        UserStats stats = userStatsRepository.findByUsuario(usuario).orElseThrow();
        stats.setCurrentLives(1);
        userStatsRepository.save(stats);

        salvarExercicio("Q1", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);
        salvarExercicio("Q2", ExerciseType.MULTIPLE_CHOICE, """
                {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                """);

        userProgressRepository.save(UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build());

        long sessionId = iniciarSessao();

        for (int i = 0; i < 2; i++) {
            long exercicioId = practiceSessionExerciseRepository
                    .findByPracticeSessionOrderByDisplayOrderAsc(
                            practiceSessionRepository.findById(sessionId).orElseThrow())
                    .get(i)
                    .getExercise()
                    .getId();
            mockMvc.perform(post("/api/aprender/sessoes/" + sessionId + "/responder")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"exercicioId":%d,"resposta":"B"}
                                    """.formatted(exercicioId)))
                    .andExpect(status().isOk());
        }

        assertEquals(0, userStatsRepository.findByUsuario(usuario).orElseThrow().getCurrentLives());
        assertTrue(notificationRepository.findAll().stream()
                .anyMatch(n -> "Sem vidas".equals(n.getTitle())));
    }

    @Test
    void modoPraticaInvalidoDeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/aprender/pratica/invalido/iniciar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Modo de prática inválido: invalido"));
    }

    private void salvarExercicio(String statement, ExerciseType type, String data) {
        exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement(statement)
                .exerciseType(type)
                .exerciseData(data)
                .xpReward(3)
                .build());
    }

    private long iniciarSessao() throws Exception {
        var result = mockMvc.perform(post("/api/aprender/modulos/" + modulo.getId() + "/iniciar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionId").asLong();
    }

    private long primeiroExercicioId(long sessionId) throws Exception {
        var sessao = practiceSessionRepository.findById(sessionId).orElseThrow();
        return practiceSessionExerciseRepository
                .findByPracticeSessionOrderByDisplayOrderAsc(sessao)
                .get(0)
                .getExercise()
                .getId();
    }
}
