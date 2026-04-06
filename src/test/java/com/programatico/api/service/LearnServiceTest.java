package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserMissionRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private UserProgressRepository userProgressRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private UserMissionRepository userMissionRepository;
    @Mock private ExerciseRepository exerciseRepository;

    @InjectMocks
    private LearnService learnService;

    // ──────────────────────────────────────────────────────────────────
    // getTrilhaComProgresso
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getTrilhaDeveRetornarTrilhaComModulosQuandoDadosExistem() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        Modulo modulo = moduloBase(track, ModuleType.STUDY, 1);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.of(track));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(track)).thenReturn(List.of(modulo));
        when(userProgressRepository.findByUsuarioAndModuloIn(any(), anyList())).thenReturn(List.of());

        TrackDto.Response response = learnService.getTrilhaComProgresso("user");

        assertNotNull(response);
        assertEquals("Lógica Básica", response.getTitulo());
        assertEquals(1, response.getModulos().size());
        assertEquals("UNLOCKED", response.getModulos().get(0).status());
        assertEquals(0, response.getPercentualConcluido());
    }

    @Test
    void getTrilhaDeveLancarExcecaoQuandoNenhumaTrilhaCadastrada() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuarioBase()));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> learnService.getTrilhaComProgresso("user"));
    }

    @Test
    void getTrilhaDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> learnService.getTrilhaComProgresso("inexistente"));
    }

    @Test
    void getTrilhaDeveCalcularProgressoCorretamenteComModulosConcluidos() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        Modulo mod1 = moduloBase(track, ModuleType.ACTIVITY, 1);
        mod1.setId(1L);
        Modulo mod2 = moduloBase(track, ModuleType.STUDY, 2);
        mod2.setId(2L);

        UserProgress progresso = UserProgress.builder()
                .usuario(usuario)
                .modulo(mod1)
                .status(ProgressStatus.COMPLETED)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.of(track));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(track)).thenReturn(List.of(mod1, mod2));
        when(userProgressRepository.findByUsuarioAndModuloIn(any(), anyList())).thenReturn(List.of(progresso));
        when(exerciseRepository.sumXpByModulo(mod1)).thenReturn(20L);

        TrackDto.Response response = learnService.getTrilhaComProgresso("user");

        assertEquals("COMPLETED", response.getModulos().get(0).status());
        assertEquals("UNLOCKED", response.getModulos().get(1).status());
        assertEquals(50, response.getPercentualConcluido());
    }

    @Test
    void getTrilhaDeveBloquearModuloQuandoAnteriorNaoConcluido() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        Modulo mod1 = moduloBase(track, ModuleType.STUDY, 1);
        mod1.setId(1L);
        Modulo mod2 = moduloBase(track, ModuleType.STUDY, 2);
        mod2.setId(2L);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.of(track));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(track)).thenReturn(List.of(mod1, mod2));
        when(userProgressRepository.findByUsuarioAndModuloIn(any(), anyList())).thenReturn(List.of());

        TrackDto.Response response = learnService.getTrilhaComProgresso("user");

        assertEquals("UNLOCKED", response.getModulos().get(0).status());
        assertEquals("LOCKED", response.getModulos().get(1).status());
    }

    @Test
    void getTrilhaDeveRetornarListaVaziaDeModulosQuandoTrilhaSemModulos() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuarioBase()));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.of(trackBase()));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        TrackDto.Response response = learnService.getTrilhaComProgresso("user");

        assertEquals(0, response.getModulos().size());
        assertEquals(0, response.getPercentualConcluido());
    }

    // ──────────────────────────────────────────────────────────────────
    // getEstatisticas
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getEstatisticasDeveRetornarStatsQuandoUsuarioPossuiRegistro() {
        Usuario usuario = usuarioBase();
        UserStats stats = UserStats.builder()
                .usuario(usuario)
                .totalXp(150)
                .currentLives(5)
                .currentStreak(3)
                .highestStreak(7)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));

        UserStatsDto.Response response = learnService.getEstatisticas("user");

        assertEquals(150, response.getTotalXp());
        assertEquals(5, response.getVidasAtuais());
        assertEquals(3, response.getSequenciaAtual());
    }

    @Test
    void getEstatisticasDeveRetornarStatsPadraoQuandoUsuarioSemRegistro() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuarioBase()));
        when(userStatsRepository.findByUsuario(any())).thenReturn(Optional.empty());

        UserStatsDto.Response response = learnService.getEstatisticas("user");

        assertNotNull(response);
        assertEquals(0, response.getTotalXp());
    }

    // ──────────────────────────────────────────────────────────────────
    // getMissoes
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getMissoesDeveRetornarListaDeMissoesComProgressoDoUsuario() {
        Usuario usuario = usuarioBase();
        Mission missao = Mission.builder()
                .id(1L)
                .title("Complete 3 módulos")
                .objectiveType("COMPLETE_MODULES")
                .xpReward(10)
                .build();
        UserMission userMission = UserMission.builder()
                .mission(missao)
                .usuario(usuario)
                .currentProgress(1)
                .isCompleted(false)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(missionRepository.findAll()).thenReturn(List.of(missao));
        when(userMissionRepository.findByUsuario(usuario)).thenReturn(List.of(userMission));

        List<UserMissionDto.Response> response = learnService.getMissoes("user");

        assertEquals(1, response.size());
        assertEquals("Complete 3 módulos", response.get(0).getTitulo());
        assertEquals(1, response.get(0).getProgressoAtual());
        assertEquals(10, response.get(0).getRecompensaXp());
    }

    @Test
    void getMissoesDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> learnService.getMissoes("inexistente"));
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private Usuario usuarioBase() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setUsername("user");
        u.setEmail("user@email.com");
        u.setSenha("hash");
        u.setAtivo(true);
        return u;
    }

    private Track trackBase() {
        return Track.builder()
                .id(1L)
                .title("Lógica Básica")
                .description("Introdução à lógica")
                .displayOrder(1)
                .build();
    }

    private Modulo moduloBase(Track track, ModuleType tipo, int ordem) {
        return Modulo.builder()
                .id((long) ordem)
                .track(track)
                .title("Módulo " + ordem)
                .moduleType(tipo)
                .displayOrder(ordem)
                .description("Descrição " + ordem)
                .build();
    }
}
