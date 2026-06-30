package com.programatico.api.service;

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
import com.programatico.api.dto.SessaoDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessaoAtividadeServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Mock private UserProgressRepository userProgressRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private NotificationService notificationService;
    @Spy private VidasService vidasService = new VidasService();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private OpenAiOrganizacaoService openAiOrganizacaoService;

    @InjectMocks
    private SessaoAtividadeService sessaoAtividadeService;

    @Test
    void iniciarPraticaFixacaoDeveRetornarAteCincoExerciciosDeModulosConcluidos() {
        Usuario usuario = usuarioBase();
        Modulo modulo1 = moduloBase(1L);
        Modulo modulo2 = moduloBase(2L);
        List<Exercise> exerciciosModulo1 = exerciciosDoModulo(modulo1, 1L, 4);
        List<Exercise> exerciciosModulo2 = exerciciosDoModulo(modulo2, 5L, 4);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of(
                        progressoConcluido(usuario, modulo1),
                        progressoConcluido(usuario, modulo2)
                ));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo1)).thenReturn(exerciciosModulo1);
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo2)).thenReturn(exerciciosModulo2);
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(99L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPratica("fixacao", "user");

        assertEquals("Prática: Fixação", response.getModuleTitle());
        assertEquals(5, response.getTotalExercises());
        assertEquals(5, response.getExercises().size());
        assertEquals(99L, response.getSessionId());
        assertEquals(5, response.getInitialLives());

        ArgumentCaptor<PracticeSession> sessaoCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionRepository).save(sessaoCaptor.capture());
        assertEquals(SessionType.QUICK_FIX, sessaoCaptor.getValue().getSessionType());
        assertEquals(usuario, sessaoCaptor.getValue().getUsuario());
    }

    @Test
    void iniciarPraticaFixacaoDeveRetornarTodosQuandoPoolTemMenosDeCincoExercicios() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> exercicios = exerciciosDoModulo(modulo, 1L, 3);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of(progressoConcluido(usuario, modulo)));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(exercicios);
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(10L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPratica("fixacao", "user");

        assertEquals(3, response.getTotalExercises());
        assertEquals(3, response.getExercises().size());
    }

    @Test
    void iniciarPraticaFixacaoDeveLancarExcecaoQuandoNaoHaModulosConcluidos() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("fixacao", "user"));

        assertEquals("Conclua um módulo antes de praticar.", ex.getMessage());
    }

    @Test
    void iniciarPraticaFixacaoDeveLancarExcecaoQuandoModulosConcluidosNaoTemExercicios() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of(progressoConcluido(usuario, modulo)));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("fixacao", "user"));

        assertEquals("Conclua um módulo antes de praticar.", ex.getMessage());
    }

    @Test
    void iniciarPraticaFixacaoDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sessaoAtividadeService.iniciarPratica("fixacao", "inexistente"));
    }

    @Test
    void iniciarPraticaFixacaoDeveAceitarModoComMaiusculas() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> exercicios = exerciciosDoModulo(modulo, 1L, 2);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of(progressoConcluido(usuario, modulo)));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(exercicios);
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(1L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPratica("FIXACAO", "user");

        assertNotNull(response);
        assertEquals(2, response.getTotalExercises());
    }

    @Test
    void iniciarPraticaCronometradaDeveRetornarTempoLimitePorXp() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> exercicios = List.of(
                exercicioComXp(modulo, 1L, 3),
                exercicioComXp(modulo, 2L, 5),
                exercicioComXp(modulo, 3L, 7)
        );

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of(progressoConcluido(usuario, modulo)));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(exercicios);
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(50L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPratica("cronometrado", "user");

        assertEquals("Prática: Cronometrado", response.getModuleTitle());
        assertEquals(3, response.getTotalExercises());
        assertTrue(response.getExercises().stream().allMatch(e -> e.getTimeLimitSeconds() != null));
        assertEquals(60, response.getExercises().stream().filter(e -> e.getXpReward() == 3).findFirst().orElseThrow().getTimeLimitSeconds());
        assertEquals(90, response.getExercises().stream().filter(e -> e.getXpReward() == 5).findFirst().orElseThrow().getTimeLimitSeconds());
        assertEquals(120, response.getExercises().stream().filter(e -> e.getXpReward() == 7).findFirst().orElseThrow().getTimeLimitSeconds());

        ArgumentCaptor<PracticeSession> sessaoCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionRepository).save(sessaoCaptor.capture());
        assertEquals(SessionType.TIMED, sessaoCaptor.getValue().getSessionType());
    }

    @Test
    void iniciarPraticaCronometradaDeveLancarExcecaoQuandoNaoHaModulosConcluidos() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userProgressRepository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED))
                .thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("cronometrado", "user"));

        assertEquals("Conclua um módulo antes de praticar.", ex.getMessage());
    }

    @Test
    void iniciarSessaoDeveRetornarExerciciosDoModulo() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> exercicios = exerciciosDoModulo(modulo, 1L, 3);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(exercicios);
        when(openAiOrganizacaoService.organizar(any(), any(), any(), anyInt())).thenReturn(exercicios);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(20L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarSessao(1L, "user");

        assertEquals(20L, response.getSessionId());
        assertEquals("Módulo 1", response.getModuleTitle());
        assertEquals(3, response.getTotalExercises());
    }

    @Test
    void responderDeveMarcarRespostaCorretaEAplicarXp() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);
        PracticeSession sessao = PracticeSession.builder()
                .id(5L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(sessionExercise));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionExerciseRepository.save(any(PracticeSessionExercise.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.RespostaRequest request = new SessaoDto.RespostaRequest(1L, "A");
        SessaoDto.RespostaResponse response = sessaoAtividadeService.responder(5L, request, "user");

        assertTrue(response.isCorrect());
        assertEquals(5, response.getRemainingLives());
        verify(practiceSessionExerciseRepository).save(sessionExercise);
    }

    @Test
    void concluirDeveCalcularRelatorioEMarcarModuloConcluido() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);
        PracticeSession sessao = PracticeSession.builder()
                .id(7L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(true)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(7L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(sessionExercise));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());
        when(userProgressRepository.save(any(UserProgress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.ConclusaoResponse response = sessaoAtividadeService.concluir(7L, "user");

        assertEquals(100, response.getAccuracy());
        assertTrue(response.isModuleCompleted());
        assertEquals(3, response.getXpEarned());
        verify(userProgressRepository).save(any(UserProgress.class));
    }

    @Test
    void iniciarPraticaErrosDeveRetornarExerciciosErrados() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of(exercise));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(30L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPratica("erros", "user");

        assertEquals("Prática: Erros", response.getModuleTitle());
        assertEquals(1, response.getTotalExercises());
    }

    @Test
    void iniciarPraticaErrosDeveFalharQuandoNaoHaErros() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("erros", "user"));

        assertEquals("Você ainda não tem erros para praticar.", ex.getMessage());
    }

    @Test
    void responderIncorretoDeveReduzirVidas() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);
        PracticeSession sessao = PracticeSession.builder()
                .id(5L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(sessionExercise));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionExerciseRepository.save(any(PracticeSessionExercise.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.RespostaResponse response = sessaoAtividadeService.responder(
                5L, new SessaoDto.RespostaRequest(1L, "B"), "user");

        assertTrue(!response.isCorrect());
        assertEquals(4, response.getRemainingLives());
    }

    @Test
    void responderDeveFalharQuandoSessaoEncerrada() {
        Usuario usuario = usuarioBase();
        PracticeSession sessao = PracticeSession.builder()
                .id(5L)
                .usuario(usuario)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));

        assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.responder(5L, new SessaoDto.RespostaRequest(1L, "A"), "user"));
    }

    @Test
    void responderDragDropCorretoDeveValidarOrdem() throws Exception {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = Exercise.builder()
                .id(1L)
                .modulo(modulo)
                .statement("Ordene")
                .exerciseType(ExerciseType.DRAG_DROP)
                .exerciseData("{\"items\":[\"primeiro\",\"segundo\"]}")
                .xpReward(5)
                .build();
        PracticeSession sessao = PracticeSession.builder()
                .id(8L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(8L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(sessionExercise));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionExerciseRepository.save(any(PracticeSessionExercise.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());

        String resposta = objectMapper.writeValueAsString(List.of("primeiro", "segundo"));
        SessaoDto.RespostaResponse response = sessaoAtividadeService.responder(
                8L, new SessaoDto.RespostaRequest(1L, resposta), "user");

        assertTrue(response.isCorrect());
    }

    @Test
    void responderDeveFalharQuandoExercicioJaRespondido() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);
        PracticeSession sessao = PracticeSession.builder()
                .id(5L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(true)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(sessionExercise));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.responder(5L, new SessaoDto.RespostaRequest(1L, "A"), "user"));

        assertEquals("Este exercício já foi respondido.", ex.getMessage());
    }

    @Test
    void concluirReplayModuloConcluidoNaoDeveSomarXp() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = exerciciosDoModulo(modulo, 1L, 1).get(0);
        PracticeSession sessao = PracticeSession.builder()
                .id(7L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(true)
                .build();
        UserProgress progresso = UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.COMPLETED)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(7L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(sessionExercise));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.of(progresso));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.ConclusaoResponse response = sessaoAtividadeService.concluir(7L, "user");

        assertEquals(0, response.getXpEarned());
        assertTrue(response.isModuleCompleted());
    }

    @Test
    void responderPairsParcialNaoDevePersistirResposta() throws Exception {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        Exercise exercise = Exercise.builder()
                .id(1L)
                .modulo(modulo)
                .statement("Pares")
                .exerciseType(ExerciseType.PAIRS)
                .exerciseData("{\"pairs\":[{\"left\":\"7\",\"right\":\"inteiro\"},{\"left\":\"2.5\",\"right\":\"decimal\"}]}")
                .xpReward(5)
                .build();
        PracticeSession sessao = PracticeSession.builder()
                .id(9L)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        PracticeSessionExercise sessionExercise = PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(9L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(sessionExercise));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));

        String respostaParcial = objectMapper.writeValueAsString(
                List.of(Map.of("left", "7", "right", "inteiro")));
        SessaoDto.RespostaResponse response = sessaoAtividadeService.responder(
                9L, new SessaoDto.RespostaRequest(1L, respostaParcial), "user");

        assertTrue(response.isCorrect());
        assertEquals(null, sessionExercise.getIsCorrect());
    }

    private Usuario usuarioBase() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("user");
        usuario.setEmail("user@email.com");
        usuario.setSenha("hash");
        usuario.setAtivo(true);
        return usuario;
    }

    private UserStats statsBase(Usuario usuario) {
        return UserStats.builder()
                .usuario(usuario)
                .totalXp(0)
                .currentLives(5)
                .currentStreak(0)
                .highestStreak(0)
                .build();
    }

    private Modulo moduloBase(Long id) {
        Track track = Track.builder()
                .id(1L)
                .title("Lógica Básica")
                .description("Introdução")
                .displayOrder(1)
                .build();
        return Modulo.builder()
                .id(id)
                .track(track)
                .title("Módulo " + id)
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(id.intValue())
                .description("Descrição")
                .build();
    }

    private UserProgress progressoConcluido(Usuario usuario, Modulo modulo) {
        return UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.COMPLETED)
                .build();
    }

    private List<Exercise> exerciciosDoModulo(Modulo modulo, long idInicial, int quantidade) {
        List<Exercise> exercicios = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            long id = idInicial + i;
            exercicios.add(Exercise.builder()
                    .id(id)
                    .modulo(modulo)
                    .statement("Enunciado " + id)
                    .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                    .exerciseData("""
                            {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                            """)
                    .xpReward(3)
                    .build());
        }
        return exercicios;
    }

    private Exercise exercicioComXp(Modulo modulo, long id, int xpReward) {
        return Exercise.builder()
                .id(id)
                .modulo(modulo)
                .statement("Enunciado " + id)
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("""
                        {"options":[{"description":"A","correct":true},{"description":"B","correct":false}]}
                        """)
                .xpReward(xpReward)
                .build();
    }
}
