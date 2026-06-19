package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.UserDailyMissionRepository;
import com.programatico.api.repository.UserStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissaoDiariaServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private UserDailyMissionRepository userDailyMissionRepository;
    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks
    private MissaoDiariaService missaoDiariaService;

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setUsername("user");
        return u;
    }

    private Mission mission(Long id, String title, String type, int xp, int qtd) {
        return Mission.builder().id(id).title(title).objectiveType(type).xpReward(xp).quantidade(qtd).build();
    }

    @Test
    void missoesDoDiaCriaConjuntoQuandoNaoExiste() {
        Usuario usuario = usuario();
        when(userDailyMissionRepository.findByUsuarioAndMissionDate(eq(usuario), any())).thenReturn(List.of());
        when(missionRepository.findAll()).thenReturn(List.of(
                mission(1L, "Conclua 1 módulo", MissaoDiariaService.COMPLETE_MODULES, 15, 1),
                mission(2L, "Acerte 10 exercícios", MissaoDiariaService.CORRECT_ANSWERS, 10, 10),
                mission(3L, "Leia 1 teoria", MissaoDiariaService.READ_PAGES, 10, 1)
        ));
        when(userDailyMissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<UserDailyMission> doDia = missaoDiariaService.missoesDoDia(usuario);

        assertEquals(3, doDia.size());
        assertEquals(1, doDia.get(0).getGoal());      // herda quantidade da missão
        assertEquals(10, doDia.get(1).getGoal());
        verify(userDailyMissionRepository, times(3)).save(any());
    }

    @Test
    void missoesDoDiaReaproveitaConjuntoExistente() {
        Usuario usuario = usuario();
        UserDailyMission existente = UserDailyMission.builder()
                .usuario(usuario).mission(mission(1L, "Conclua 1 módulo", MissaoDiariaService.COMPLETE_MODULES, 15, 1))
                .goal(1).currentProgress(0).completed(false).build();
        when(userDailyMissionRepository.findByUsuarioAndMissionDate(eq(usuario), any()))
                .thenReturn(List.of(existente));

        List<UserDailyMission> doDia = missaoDiariaService.missoesDoDia(usuario);

        assertEquals(1, doDia.size());
        verify(missionRepository, never()).findAll();
        verify(userDailyMissionRepository, never()).save(any());
    }

    @Test
    void registrarProgressoCompletaMissaoESomaXpDeRecompensa() {
        Usuario usuario = usuario();
        UserDailyMission modulo = UserDailyMission.builder()
                .usuario(usuario).mission(mission(1L, "Conclua 1 módulo", MissaoDiariaService.COMPLETE_MODULES, 15, 1))
                .goal(1).currentProgress(0).completed(false).build();
        UserDailyMission xp = UserDailyMission.builder()
                .usuario(usuario).mission(mission(2L, "Ganhe 30 XP hoje", MissaoDiariaService.EARN_XP, 10, 30))
                .goal(30).currentProgress(20).completed(false).build();
        UserStats stats = UserStats.builder().usuario(usuario).totalXp(100).build();

        when(userDailyMissionRepository.findByUsuarioAndMissionDate(eq(usuario), any()))
                .thenReturn(List.of(modulo, xp));
        when(userDailyMissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.findByUsuario(usuario)).thenReturn(Optional.of(stats));

        List<String> concluidas = missaoDiariaService.registrarProgresso(
                usuario, Map.of(MissaoDiariaService.COMPLETE_MODULES, 1, MissaoDiariaService.EARN_XP, 5));

        assertEquals(List.of("Conclua 1 módulo"), concluidas);   // só a de módulo completou
        assertTrue(modulo.getCompleted());
        assertFalse(xp.getCompleted());
        assertEquals(25, xp.getCurrentProgress());               // 20 + 5, ainda < 30
        assertEquals(115, stats.getTotalXp());                   // 100 + 15 de recompensa
        verify(userStatsRepository).save(stats);
    }

    @Test
    void registrarProgressoIgnoraMissaoJaConcluida() {
        Usuario usuario = usuario();
        UserDailyMission jaConcluida = UserDailyMission.builder()
                .usuario(usuario).mission(mission(1L, "Conclua 1 módulo", MissaoDiariaService.COMPLETE_MODULES, 15, 1))
                .goal(1).currentProgress(1).completed(true).build();
        when(userDailyMissionRepository.findByUsuarioAndMissionDate(eq(usuario), any()))
                .thenReturn(List.of(jaConcluida));

        List<String> concluidas = missaoDiariaService.registrarProgresso(
                usuario, Map.of(MissaoDiariaService.COMPLETE_MODULES, 1));

        assertTrue(concluidas.isEmpty());
        verify(userDailyMissionRepository, never()).save(any());
        verify(userStatsRepository, never()).save(any());
    }
}
