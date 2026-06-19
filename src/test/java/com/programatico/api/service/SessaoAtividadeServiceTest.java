package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
