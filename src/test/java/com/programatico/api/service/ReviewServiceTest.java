package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.ReviewDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserMissionRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private UserMissionRepository userMissionRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void getReviewDeveRetornarAgregadosDeSessaoMissaoEAssunto() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        Modulo modulo = moduloBase(track, 1);
        Mission mission = Mission.builder()
                .id(1L)
                .title("Treinar logica")
                .quantidade(3)
                .build();
        UserMission userMission = UserMission.builder()
                .usuario(usuario)
                .mission(mission)
                .currentProgress(2)
                .isCompleted(false)
                .build();
        UserStats stats = UserStats.builder()
                .usuario(usuario)
                .totalXp(250)
                .build();
        LocalDateTime startedAt = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        PracticeSession session = PracticeSession.builder()
                .id(10L)
                .usuario(usuario)
                .modulo(modulo)
                .startedAt(startedAt)
                .endedAt(startedAt.plusMinutes(1))
                .build();
        Exercise exercise = Exercise.builder()
                .id(100L)
                .modulo(modulo)
                .statement("Pergunta")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(5)
                .tags("Fluxo logico, Logica base")
                .build();
        PracticeSessionExercise answer1 = PracticeSessionExercise.builder()
                .practiceSession(session)
                .exercise(exercise)
                .isCorrect(true)
                .build();
        PracticeSessionExercise answer2 = PracticeSessionExercise.builder()
                .practiceSession(session)
                .exercise(exercise)
                .isCorrect(false)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(track));
        when(practiceSessionRepository.findByUsuarioAndModulo_TrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                any(), any(), any())).thenReturn(List.of(session));
        when(practiceSessionExerciseRepository.findByPracticeSessionIn(List.of(session))).thenReturn(List.of(answer1, answer2));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));
        when(userMissionRepository.findByUsuario(usuario)).thenReturn(List.of(userMission));
        when(missionRepository.findAll()).thenReturn(List.of(mission));

        ReviewDto.Response response = reviewService.getReview("user", 1L, 7);

        assertEquals(1L, response.getSelectedTrackId());
        assertEquals(7, response.getSelectedDays());
        assertEquals(250, response.getCurrentXp());
        assertEquals("Exercicios feitos", response.getStats().get(0).getTitle());
        assertEquals("2", response.getStats().get(0).getValue());
        assertEquals(7, response.getPerformanceData().size());
        assertTrue(response.getSubjectAccuracy().stream()
                .map(ReviewDto.SubjectAccuracyItem::getAssunto)
                .anyMatch(subject -> subject.equals("Fluxo logico")));
        assertEquals("Treinar logica", response.getRecentMissions().get(0).getLabel());
        assertEquals("Em progresso (2/3)", response.getRecentMissions().get(0).getStatus());
        assertEquals("30s", response.getStats().get(3).getValue());
    }

    @Test
    void getReviewDeveFalharQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.getReview("inexistente", null, 7));
    }

    @Test
    void getReviewDeveFalharQuandoTrilhaInvalida() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(track));

        assertThrows(BadRequestException.class,
                () -> reviewService.getReview("user", 999L, 7));
    }

    @Test
    void getReviewDeveNormalizarDiasInvalidosParaSete() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(track));
        when(practiceSessionRepository.findByUsuarioAndModulo_TrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                any(), any(), any())).thenReturn(List.of());
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.empty());
        when(userMissionRepository.findByUsuario(usuario)).thenReturn(List.of());
        when(missionRepository.findAll()).thenReturn(List.of());

        ReviewDto.Response response = reviewService.getReview("user", 1L, 45);

        assertEquals(7, response.getSelectedDays());
        assertEquals("0", response.getStats().get(0).getValue());
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

    private Track trackBase() {
        return Track.builder()
                .id(1L)
                .title("Logica Basica")
                .description("Introducao a logica")
                .displayOrder(1)
                .build();
    }

    private Modulo moduloBase(Track track, int ordem) {
        return Modulo.builder()
                .id((long) ordem)
                .track(track)
                .title("Modulo " + ordem)
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(ordem)
                .description("Descricao " + ordem)
                .build();
    }
}
