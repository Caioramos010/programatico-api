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
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.SubscriptionType;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
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
    @Mock private MissaoDiariaService missaoDiariaService;

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
    void iniciarPraticaErrosPorAssuntoDeveLancarExcecaoParaNaoRoot() {
        Usuario usuario = usuarioBase(); // FREE por padrão
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPraticaErrosPorAssunto("decisão", "user"));

        assertTrue(ex.getMessage().contains("Root"));
    }

    @Test
    void iniciarPraticaErrosPorAssuntoDeveMontarSessaoSomenteComErrosDoAssunto() {
        Usuario usuario = usuarioBase();
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        Exercise e1 = exercicioComTags(1L, "decisão, condição");
        Exercise e2 = exercicioComTags(2L, "sequência");
        Exercise e3 = exercicioComTags(3L, "decisão");

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of(e1, e2, e3));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(77L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse response = sessaoAtividadeService.iniciarPraticaErrosPorAssunto("decisão", "user");

        assertEquals("Revisar: decisão", response.getModuleTitle());
        assertEquals(2, response.getTotalExercises()); // só e1 e e3 têm a tag
    }

    @Test
    void iniciarPraticaErrosPorAssuntoDeveLancarExcecaoQuandoNaoHaErrosNoAssunto() {
        Usuario usuario = usuarioBase();
        usuario.setSubscriptionType(SubscriptionType.ROOT);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of(exercicioComTags(1L, "sequência")));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPraticaErrosPorAssunto("decisão", "user"));

        assertEquals("Você não tem erros nesse assunto para revisar.", ex.getMessage());
    }

    @Test
    void iniciarSessaoDeveRetomarSessaoAbertaDoModuloOndeParou() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession aberta = PracticeSession.builder()
                .id(55L).usuario(usuario).modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        PracticeSessionExercise i1 = PracticeSessionExercise.builder()
                .practiceSession(aberta).exercise(exercicioComTags(1L, "x")).displayOrder(1)
                .isCorrect(true).mastered(true).build();  // dominado -> sai da fila
        PracticeSessionExercise i2 = PracticeSessionExercise.builder()
                .practiceSession(aberta).exercise(exercicioComTags(2L, "y")).displayOrder(2)
                .isCorrect(false).mastered(false).build(); // errado -> ainda pendente
        PracticeSessionExercise i3 = PracticeSessionExercise.builder()
                .practiceSession(aberta).exercise(exercicioComTags(3L, "z")).displayOrder(3).build(); // não respondido

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo))
                .thenReturn(Optional.of(aberta));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(aberta))
                .thenReturn(List.of(i1, i2, i3));

        SessaoDto.InicioResponse resp = sessaoAtividadeService.iniciarSessao(1L, "user");

        assertEquals(55L, resp.getSessionId());        // mesma sessão (retomada)
        assertEquals(3, resp.getTotalExercises());      // mantém o total (todos os alvos)
        assertEquals(List.of(1L), resp.getMasteredIds()); // i1 dominado -> fora da fila no front
    }

    // ── iniciarSessao: criação de sessão nova ──────────────────────────────────

    @Test
    void iniciarSessaoDeveCriarSessaoNovaQuandoNaoHaSessaoAbertaUsandoSelecaoDeterministica() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> candidatos = exerciciosDoModulo(modulo, 1L, 4);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo))
                .thenReturn(Optional.empty());
        when(openAiOrganizacaoService.organizar(anyList(), any(), any(), anyInt())).thenReturn(List.of());
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(candidatos);
        when(exerciseRepository.findByModuloAndXpReward(any(Modulo.class), any())).thenReturn(List.of());
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(200L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse resp = sessaoAtividadeService.iniciarSessao(1L, "user");

        assertEquals(200L, resp.getSessionId());
        assertEquals(4, resp.getTotalExercises());
        assertEquals(modulo.getTitle(), resp.getModuleTitle());
        assertTrue(resp.getMasteredIds().isEmpty());

        ArgumentCaptor<PracticeSession> captor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionRepository).save(captor.capture());
        assertEquals(SessionType.ACTIVITY, captor.getValue().getSessionType());
        assertEquals(modulo, captor.getValue().getModulo());
    }

    @Test
    void iniciarSessaoDeveUsarSelecaoDaIaQuandoOpenAiRetornaExercicios() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        List<Exercise> candidatos = exerciciosDoModulo(modulo, 1L, 4);
        List<Exercise> ordemIa = List.of(candidatos.get(3), candidatos.get(0));

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo))
                .thenReturn(Optional.empty());
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(candidatos);
        when(openAiOrganizacaoService.organizar(anyList(), any(), any(), anyInt())).thenReturn(ordemIa);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(201L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse resp = sessaoAtividadeService.iniciarSessao(1L, "user");

        assertEquals(2, resp.getTotalExercises()); // só o que a IA devolveu
        // IA bypassa selecionarExercicios/findByModuloAndXpReward
        verify(exerciseRepository, never()).findByModuloAndXpReward(any(Modulo.class), any());
    }

    @Test
    void iniciarSessaoDeveEncerrarSessaoAbertaTotalmenteDominadaECriarNova() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession aberta = PracticeSession.builder()
                .id(33L).usuario(usuario).modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        PracticeSessionExercise dominado = PracticeSessionExercise.builder()
                .practiceSession(aberta).exercise(exercicioComTags(1L, "x")).displayOrder(1)
                .isCorrect(true).mastered(true).build();
        List<Exercise> candidatos = exerciciosDoModulo(modulo, 10L, 3);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo))
                .thenReturn(Optional.of(aberta));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(aberta))
                .thenReturn(List.of(dominado));
        when(openAiOrganizacaoService.organizar(anyList(), any(), any(), anyInt())).thenReturn(List.of());
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(candidatos);
        when(exerciseRepository.findByModuloAndXpReward(any(Modulo.class), any())).thenReturn(List.of());
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            if (sessao.getId() == null) sessao.setId(202L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse resp = sessaoAtividadeService.iniciarSessao(1L, "user");

        assertNotNull(aberta.getEndedAt()); // sessão antiga foi encerrada
        assertEquals(202L, resp.getSessionId()); // sessão nova
        assertEquals(3, resp.getTotalExercises());
    }

    @Test
    void iniciarSessaoDeveLancarExcecaoQuandoModuloNaoTemExercicios() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo))
                .thenReturn(Optional.empty());
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarSessao(1L, "user"));

        assertEquals("Este módulo não possui exercícios cadastrados.", ex.getMessage());
    }

    @Test
    void iniciarSessaoDeveLancarExcecaoQuandoModuloNaoExiste() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sessaoAtividadeService.iniciarSessao(999L, "user"));
    }

    // ── responder ───────────────────────────────────────────────────────────────

    @Test
    void responderDeveCreditarXpNaPrimeiraTentativaCorretaEMarcarDominado() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise exercise = exercicioComTags(1L, "[\"decisão\"]");
        exercise.setXpReward(7);
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(exercise).displayOrder(1).build();
        UserStats stats = statsBase(usuario);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "\"A\""), "user");

        assertTrue(resp.isCorrect());
        assertEquals("A", resp.getCorrectAnswer());
        assertEquals(List.of("decisão"), resp.getRelatedTopics());
        assertTrue(pse.getIsCorrect());
        assertTrue(pse.getMastered());
        assertEquals(7, stats.getTotalXp()); // XP creditado na 1ª tentativa
    }

    @Test
    void responderErradoDevePerderVidaParaUsuarioNaoRoot() {
        Usuario usuario = usuarioBase(); // FREE
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise exercise = exercicioComTags(1L, "[\"loop\"]");
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(exercise).displayOrder(1).build();
        UserStats stats = statsBase(usuario); // 5 vidas

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "\"B\""), "user"); // B é incorreta

        assertEquals(false, resp.isCorrect());
        assertEquals(4, resp.getRemainingLives()); // perdeu 1 vida
        assertEquals(false, pse.getIsCorrect());
        assertEquals(0, stats.getTotalXp()); // sem XP no erro
    }

    @Test
    void responderErradoQueZeraVidasDeveNotificarSemVidas() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise exercise = exercicioComTags(1L, "[\"loop\"]");
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(exercise).displayOrder(1).build();
        UserStats stats = statsBase(usuario);
        stats.setCurrentLives(1); // última vida

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "\"B\""), "user");

        assertEquals(0, resp.getRemainingLives());
        verify(notificationService).criarNotificacaoSistema(
                any(Usuario.class), any(), any(), any(NotificationKind.class));
    }

    @Test
    void responderRootErradoNaoPerdeVida() {
        Usuario usuario = usuarioBase();
        usuario.setSubscriptionType(SubscriptionType.ROOT); // vidas ilimitadas
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise exercise = exercicioComTags(1L, "[\"loop\"]");
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(exercise).displayOrder(1).build();
        UserStats stats = statsBase(usuario);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "\"B\""), "user");

        assertEquals(false, resp.isCorrect());
        assertEquals(5, resp.getRemainingLives()); // Root não perde vida
        verify(notificationService, never()).criarNotificacaoSistema(any(), any(), any(), any());
    }

    @Test
    void responderNaoCreditaXpEmReplayDeModuloConcluido() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise exercise = exercicioComTags(1L, "[\"decisão\"]");
        exercise.setXpReward(5);
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(exercise).displayOrder(1).build();
        UserStats stats = statsBase(usuario);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo))
                .thenReturn(Optional.of(progressoConcluido(usuario, modulo)));

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "\"A\""), "user");

        assertTrue(resp.isCorrect());
        assertEquals(0, stats.getTotalXp()); // replay não pontua
        assertTrue(pse.getMastered());
    }

    @Test
    void responderExercicioForaDaSessaoDeveValidarReforcoSemMarcarPse() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise reforco = exercicioComTags(9L, "[\"reforço\"]");

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 9L))
                .thenReturn(Optional.empty()); // não é alvo da sessão
        when(exerciseRepository.findById(9L)).thenReturn(Optional.of(reforco));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        // sessionExercise == null -> primeiraTentativa false, então XP/moduloJaConcluido nem é avaliado

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(9L, "\"A\""), "user");

        assertTrue(resp.isCorrect());
        verify(practiceSessionExerciseRepository, never()).save(any());
    }

    @Test
    void responderDeveLancarExcecaoQuandoExercicioDeReforcoNaoExiste() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 404L))
                .thenReturn(Optional.empty());
        when(exerciseRepository.findById(404L)).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.responder(5L, request(404L, "\"A\""), "user"));

        assertEquals("Exercício não encontrado.", ex.getMessage());
    }

    @Test
    void responderDeveLancarExcecaoQuandoSessaoJaEncerrada() {
        Usuario usuario = usuarioBase();
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, moduloBase(1L));
        sessao.setEndedAt(LocalDateTime.now());

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.responder(5L, request(1L, "\"A\""), "user"));

        assertEquals("Esta sessão já foi encerrada.", ex.getMessage());
    }

    @Test
    void responderDeveLancarExcecaoQuandoSessaoNaoExiste() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(404L, usuario)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sessaoAtividadeService.responder(404L, request(1L, "\"A\""), "user"));
    }

    @Test
    void responderPairsParcialCorretoRetornaSemMarcarComoRespondido() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise pairs = Exercise.builder()
                .id(1L).modulo(modulo).statement("Pares")
                .exerciseType(ExerciseType.PAIRS)
                .exerciseData("{\"pairs\":[{\"left\":\"a\",\"right\":\"1\"},{\"left\":\"b\",\"right\":\"2\"}]}")
                .xpReward(5).tags("[\"pares\"]").build();
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(pairs).displayOrder(1).build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));

        // só 1 par enviado (de 2 corretos) -> parcial
        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "[{\"left\":\"a\",\"right\":\"1\"}]"), "user");

        assertTrue(resp.isCorrect());
        assertEquals("", resp.getCorrectAnswer());
        assertNull(pse.getIsCorrect()); // não foi marcado (parcial)
        verify(practiceSessionExerciseRepository, never()).save(any());
    }

    @Test
    void responderDragDropCorretoMarcaDominado() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise dragDrop = Exercise.builder()
                .id(1L).modulo(modulo).statement("Ordene")
                .exerciseType(ExerciseType.DRAG_DROP)
                .exerciseData("{\"items\":[\"um\",\"dois\",\"tres\"]}")
                .xpReward(3).tags("[\"ordem\"]").build();
        PracticeSessionExercise pse = PracticeSessionExercise.builder()
                .practiceSession(sessao).exercise(dragDrop).displayOrder(1).build();
        UserStats stats = statsBase(usuario);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionAndExerciseId(sessao, 1L))
                .thenReturn(Optional.of(pse));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());

        SessaoDto.RespostaResponse resp = sessaoAtividadeService.responder(
                5L, request(1L, "[\"um\",\"dois\",\"tres\"]"), "user");

        assertTrue(resp.isCorrect());
        assertTrue(pse.getMastered());
        assertEquals(3, stats.getTotalXp());
    }

    // ── concluir ─────────────────────────────────────────────────────────────────

    @Test
    void concluirDeveCompletarModuloQuandoTodosDominadosENotificarPrimeiraConclusao() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        sessao.setStartedAt(LocalDateTime.now().minusMinutes(2));
        PracticeSessionExercise a = pseRespondido(sessao, exercicioComTags(1L, "[\"decisão\"]"), true, true);
        PracticeSessionExercise b = pseRespondido(sessao, exercicioComTags(2L, "[\"loop\"]"), true, true);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(a, b));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(missaoDiariaService.registrarProgresso(any(), anyMap())).thenReturn(List.of());

        SessaoDto.ConclusaoResponse resp = sessaoAtividadeService.concluir(5L, "user");

        assertTrue(resp.isModuleCompleted());
        assertTrue(resp.isFirstCompletion());
        assertEquals(100, resp.getAccuracy());
        assertEquals(6, resp.getXpEarned()); // 3 + 3
        assertNotNull(sessao.getEndedAt());
        verify(userProgressRepository).save(any(UserProgress.class));
        verify(notificationService).criarNotificacaoSistema(
                any(Usuario.class), any(), any(), any(NotificationKind.class));

        ArgumentCaptor<java.util.Map<String, Integer>> captor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(missaoDiariaService).registrarProgresso(any(), captor.capture());
        assertTrue(captor.getValue().containsKey(MissaoDiariaService.COMPLETE_MODULES));
        assertTrue(captor.getValue().containsKey(MissaoDiariaService.PERFECT_SESSION));
    }

    @Test
    void concluirReplayDeModuloConcluidoNaoPontuaNemNotifica() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        sessao.setStartedAt(LocalDateTime.now().minusMinutes(1));
        PracticeSessionExercise a = pseRespondido(sessao, exercicioComTags(1L, "[\"decisão\"]"), true, true);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(a));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo))
                .thenReturn(Optional.of(progressoConcluido(usuario, modulo)));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(missaoDiariaService.registrarProgresso(any(), anyMap())).thenReturn(List.of());

        SessaoDto.ConclusaoResponse resp = sessaoAtividadeService.concluir(5L, "user");

        assertEquals(0, resp.getXpEarned()); // replay não pontua
        assertFalse(resp.isFirstCompletion());
        verify(notificationService, never()).criarNotificacaoSistema(any(), any(), any(), any());
    }

    @Test
    void concluirSemDominarTodosNaoCompletaModuloMasMontaSubjectReview() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        sessao.setStartedAt(LocalDateTime.now().minusSeconds(30));
        PracticeSessionExercise acertou = pseRespondido(sessao, exercicioComTags(1L, "[\"decisão\"]"), true, true);
        PracticeSessionExercise errou = pseRespondido(sessao, exercicioComTags(2L, "[\"decisão\"]"), false, false);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(acertou, errou));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(missaoDiariaService.registrarProgresso(any(), anyMap())).thenReturn(List.of());

        SessaoDto.ConclusaoResponse resp = sessaoAtividadeService.concluir(5L, "user");

        assertFalse(resp.isModuleCompleted());
        assertEquals(50, resp.getAccuracy()); // 1 de 2
        verify(userProgressRepository, never()).save(any());
        assertEquals(1, resp.getSubjectReview().size());
        SessaoDto.SubjectReview review = resp.getSubjectReview().get(0);
        assertEquals("decisão", review.getAssunto());
        assertEquals(1, review.getAcertos());
        assertEquals(1, review.getErros());
    }

    @Test
    void concluirPraticaDeErrosIncrementaMissaoPracticeErrors() {
        Usuario usuario = usuarioBase();
        PracticeSession sessao = PracticeSession.builder()
                .id(5L).usuario(usuario).modulo(null)
                .sessionType(SessionType.ERRORS)
                .startedAt(LocalDateTime.now().minusSeconds(10))
                .build();
        PracticeSessionExercise a = pseRespondido(sessao, exercicioComTags(1L, "[\"x\"]"), true, true);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(practiceSessionExerciseRepository.findByPracticeSessionOrderByDisplayOrderAsc(sessao))
                .thenReturn(List.of(a));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(missaoDiariaService.registrarProgresso(any(), anyMap())).thenReturn(List.of("Praticar erros"));

        SessaoDto.ConclusaoResponse resp = sessaoAtividadeService.concluir(5L, "user");

        assertFalse(resp.isModuleCompleted()); // sem módulo
        assertEquals(List.of("Praticar erros"), resp.getCompletedMissions());

        ArgumentCaptor<java.util.Map<String, Integer>> captor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(missaoDiariaService).registrarProgresso(any(), captor.capture());
        assertTrue(captor.getValue().containsKey(MissaoDiariaService.PRACTICE_ERRORS));
    }

    @Test
    void concluirDeveLancarExcecaoQuandoSessaoJaEncerrada() {
        Usuario usuario = usuarioBase();
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, moduloBase(1L));
        sessao.setEndedAt(LocalDateTime.now());

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.concluir(5L, "user"));

        assertEquals("Esta sessão já foi encerrada.", ex.getMessage());
    }

    // ── buscarReforco ────────────────────────────────────────────────────────────

    @Test
    void buscarReforcoDeveRetornarExercicioComTagEmComum() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise base = exercicioComTags(1L, "[\"decisão\"]");
        Exercise candidato = exercicioComTags(2L, "[\"decisão\",\"loop\"]");
        Exercise semTagComum = exercicioComTags(3L, "[\"sequência\"]");

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(base));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo))
                .thenReturn(List.of(base, candidato, semTagComum));

        SessaoDto.ExercicioSessao resp = sessaoAtividadeService.buscarReforco(
                5L, 1L, List.of(), "user");

        assertNotNull(resp);
        assertEquals(2L, resp.getId()); // único candidato com tag em comum (base é excluído)
    }

    @Test
    void buscarReforcoDeveRetornarNullQuandoSessaoSemModulo() {
        Usuario usuario = usuarioBase();
        PracticeSession sessao = PracticeSession.builder()
                .id(5L).usuario(usuario).modulo(null).sessionType(SessionType.ERRORS).build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));

        assertNull(sessaoAtividadeService.buscarReforco(5L, 1L, List.of(), "user"));
    }

    @Test
    void buscarReforcoDeveRetornarNullQuandoBaseSemTags() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise base = exercicioComTags(1L, null);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(base));

        assertNull(sessaoAtividadeService.buscarReforco(5L, 1L, List.of(), "user"));
    }

    @Test
    void buscarReforcoDeveRetornarNullQuandoNaoHaCandidatos() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);
        Exercise base = exercicioComTags(1L, "[\"decisão\"]");
        Exercise outro = exercicioComTags(2L, "[\"sequência\"]"); // sem tag em comum

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(base));
        when(exerciseRepository.findByModuloOrderByIdAsc(modulo)).thenReturn(List.of(base, outro));

        assertNull(sessaoAtividadeService.buscarReforco(5L, 1L, List.of(2L), "user"));
    }

    @Test
    void buscarReforcoDeveLancarExcecaoQuandoExercicioBaseNaoExiste() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(1L);
        PracticeSession sessao = sessaoAtivaDoModulo(5L, usuario, modulo);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionRepository.findByIdAndUsuario(5L, usuario)).thenReturn(Optional.of(sessao));
        when(exerciseRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.buscarReforco(5L, 404L, List.of(), "user"));
    }

    // ── iniciarPratica: erros ─────────────────────────────────────────────────────

    @Test
    void iniciarPraticaErrosDeveMontarSessaoComErrosDistintos() {
        Usuario usuario = usuarioBase();
        Exercise e1 = exercicioComTags(1L, "[\"loop\"]");
        Exercise e2 = exercicioComTags(2L, "[\"decisão\"]");

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of(e1, e2, e1)); // duplicado deve ser distinct
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(statsBase(usuario)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(inv -> {
            PracticeSession sessao = inv.getArgument(0);
            sessao.setId(88L);
            return sessao;
        });
        when(practiceSessionExerciseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SessaoDto.InicioResponse resp = sessaoAtividadeService.iniciarPratica("erros", "user");

        assertEquals("Prática: Erros", resp.getModuleTitle());
        assertEquals(2, resp.getTotalExercises()); // distinct

        ArgumentCaptor<PracticeSession> captor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionRepository).save(captor.capture());
        assertEquals(SessionType.ERRORS, captor.getValue().getSessionType());
    }

    @Test
    void iniciarPraticaErrosDeveLancarExcecaoQuandoNaoHaErros() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario))
                .thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("erros", "user"));

        assertEquals("Você ainda não tem erros para praticar.", ex.getMessage());
    }

    @Test
    void iniciarPraticaDeveLancarExcecaoParaModoInvalido() {
        Usuario usuario = usuarioBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessaoAtividadeService.iniciarPratica("inexistente", "user"));

        assertTrue(ex.getMessage().contains("Modo de prática inválido"));
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

    private Exercise exercicioComTags(long id, String tags) {
        return Exercise.builder()
                .id(id)
                .modulo(moduloBase(1L))
                .statement("Enunciado " + id)
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{\"options\":[{\"description\":\"A\",\"correct\":true},{\"description\":\"B\",\"correct\":false}]}")
                .xpReward(3)
                .tags(tags)
                .build();
    }

    private PracticeSession sessaoAtivaDoModulo(Long id, Usuario usuario, Modulo modulo) {
        return PracticeSession.builder()
                .id(id)
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
    }

    private PracticeSessionExercise pseRespondido(PracticeSession sessao, Exercise exercise,
            boolean correto, boolean dominado) {
        return PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exercise)
                .displayOrder(1)
                .isCorrect(correto)
                .mastered(dominado)
                .build();
    }

    private SessaoDto.RespostaRequest request(Long exercicioId, String resposta) {
        return new SessaoDto.RespostaRequest(exercicioId, resposta);
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
