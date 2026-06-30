package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.dto.TheoryDto;
import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private UserProgressRepository userProgressRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private MissaoDiariaService missaoDiariaService;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private TeoriaPaginaRepository teoriaPaginaRepository;
    @Mock private ContentBlockRepository contentBlockRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Spy private VidasService vidasService = new VidasService();
    @Mock private NotificationService notificationService;

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
        assertEquals("Lógica Básica", response.getTitle());
        assertEquals(1, response.getModules().size());
        assertEquals("UNLOCKED", response.getModules().get(0).status());
        assertEquals(0, response.getCompletedPercentage());
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

        assertEquals("COMPLETED", response.getModules().get(0).status());
        assertEquals("UNLOCKED", response.getModules().get(1).status());
        assertEquals(50, response.getCompletedPercentage());
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

        assertEquals("UNLOCKED", response.getModules().get(0).status());
        assertEquals("LOCKED", response.getModules().get(1).status());
    }

    @Test
    void getTrilhaDeveRetornarListaVaziaDeModulosQuandoTrilhaSemModulos() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuarioBase()));
        when(trackRepository.findFirstByOrderByDisplayOrderAsc()).thenReturn(Optional.of(trackBase()));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        TrackDto.Response response = learnService.getTrilhaComProgresso("user");

        assertEquals(0, response.getModules().size());
        assertEquals(0, response.getCompletedPercentage());
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
        assertEquals(5, response.getCurrentLives());
        assertEquals(3, response.getCurrentStreak());
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
                .title("Conclua 1 módulo")
                .objectiveType("COMPLETE_MODULES")
                .xpReward(10)
                .quantidade(1)
                .build();
        UserDailyMission udm = UserDailyMission.builder()
                .mission(missao)
                .usuario(usuario)
                .currentProgress(1)
                .goal(1)
                .completed(false)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(missaoDiariaService.missoesDoDia(usuario)).thenReturn(List.of(udm));

        List<UserMissionDto.Response> response = learnService.getMissoes("user");

        assertEquals(1, response.size());
        assertEquals("Conclua 1 módulo", response.get(0).getTitle());
        assertEquals(1, response.get(0).getCurrentProgress());
        assertEquals(1, response.get(0).getGoal());
        assertEquals(10, response.get(0).getXpReward());
    }

    @Test
    void getMissoesDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> learnService.getMissoes("inexistente"));
    }

    // ──────────────────────────────────────────────────────────────────
    // getTeorico
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getTeoricoDeveRetornarPaginasComBlocosQuandoModuloStudyDesbloqueado() {
        Usuario usuario = usuarioBase();
        Track track = trackBase();
        Modulo modulo = moduloBase(track, ModuleType.STUDY, 1);

        TeoriaPagina page = TeoriaPagina.builder()
                .id(10L)
                .modulo(modulo)
                .title("Página 1")
                .description("Intro")
                .displayOrder(1)
                .build();

        ContentBlock block = ContentBlock.builder()
                .id(100L)
                .modulo(modulo)
                .pagina(page)
                .layoutType(LayoutType.TEXT)
                .textContent("Olá mundo")
                .displayOrder(1)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());
        when(teoriaPaginaRepository.findByModuloOrderByDisplayOrderAsc(modulo)).thenReturn(List.of(page));
        when(contentBlockRepository.findByPaginaOrderByDisplayOrderAsc(page)).thenReturn(List.of(block));

        TheoryDto.Response response = learnService.getTeorico(1L, "user");

        assertNotNull(response);
        assertEquals(1L, response.getModuleId());
        assertEquals("Módulo 1", response.getModuleTitle());
        assertEquals(1, response.getPages().size());
        TheoryDto.Page firstPage = response.getPages().get(0);
        assertEquals("Página 1", firstPage.getTitle());
        assertEquals(1, firstPage.getBlocks().size());
        TheoryDto.Block firstBlock = firstPage.getBlocks().get(0);
        assertEquals(LayoutType.TEXT, firstBlock.getLayoutType());
        assertEquals("Olá mundo", firstBlock.getTextContent());
    }

    @Test
    void getTeoricoDeveLancarExcecaoQuandoModuloNaoEStudy() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(trackBase(), ModuleType.ACTIVITY, 1);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> learnService.getTeorico(1L, "user"));
        assertEquals("Este módulo não é teórico.", ex.getMessage());
    }

    @Test
    void getTeoricoDeveLancarExcecaoQuandoModuloLocked() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(trackBase(), ModuleType.STUDY, 1);
        UserProgress progresso = UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.LOCKED)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.of(progresso));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> learnService.getTeorico(1L, "user"));
        assertEquals("Módulo bloqueado.", ex.getMessage());
    }

    @Test
    void getTeoricoDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> learnService.getTeorico(1L, "inexistente"));
    }

    @Test
    void getTeoricoDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuarioBase()));
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> learnService.getTeorico(99L, "user"));
    }

    // ──────────────────────────────────────────────────────────────────
    // concluirTeorico
    // ──────────────────────────────────────────────────────────────────

    @Test
    void concluirTeoricoDeveSalvarProgressoComoCompletedCriandoRegistroSeNaoExiste() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(trackBase(), ModuleType.STUDY, 1);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.empty());

        learnService.concluirTeorico(1L, "user");

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(userProgressRepository).save(captor.capture());
        UserProgress saved = captor.getValue();
        assertEquals(ProgressStatus.COMPLETED, saved.getStatus());
        assertNotNull(saved.getCompletedAt());
        assertEquals(usuario, saved.getUsuario());
        assertEquals(modulo, saved.getModulo());
    }

    @Test
    void concluirTeoricoDeveAtualizarProgressoExistente() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(trackBase(), ModuleType.STUDY, 1);
        UserProgress existente = UserProgress.builder()
                .id(50L)
                .usuario(usuario)
                .modulo(modulo)
                .status(ProgressStatus.UNLOCKED)
                .build();

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(userProgressRepository.findByUsuarioAndModulo(usuario, modulo)).thenReturn(Optional.of(existente));

        learnService.concluirTeorico(1L, "user");

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(userProgressRepository).save(captor.capture());
        UserProgress saved = captor.getValue();
        assertEquals(50L, saved.getId());
        assertEquals(ProgressStatus.COMPLETED, saved.getStatus());
        assertNotNull(saved.getCompletedAt());
    }

    @Test
    void concluirTeoricoDeveLancarExcecaoQuandoModuloNaoEStudy() {
        Usuario usuario = usuarioBase();
        Modulo modulo = moduloBase(trackBase(), ModuleType.ACTIVITY, 1);

        when(usuarioRepository.findByUsername("user")).thenReturn(Optional.of(usuario));
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> learnService.concluirTeorico(1L, "user"));
        assertEquals("Este módulo não é teórico.", ex.getMessage());
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
