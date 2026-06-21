package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.dto.MissaoDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.MissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMissaoServiceTest {

    @Mock private MissionRepository missionRepository;

    @InjectMocks
    private AdminMissaoService adminMissaoService;

    @Test
    void listarTodasDeveRetornarListaDeMissoes() {
        Mission mission = missionBase();
        when(missionRepository.findAll()).thenReturn(List.of(mission));

        List<MissaoDto.Response> response = adminMissaoService.listarTodas();

        assertEquals(1, response.size());
        assertEquals("Resolva 5 exercícios", response.get(0).getTitle());
        assertEquals("RESOLVER_EXERCICIOS", response.get(0).getObjectiveType());
        assertEquals(5, response.get(0).getQuantity());
    }

    @Test
    void criarDevePersistirMissaoComDadosDaRequest() {
        MissaoDto.Request request = MissaoDto.Request.builder()
                .title("Nova Missão")
                .objectiveType("GANHAR_XP")
                .xpReward(100)
                .quantity(3)
                .build();
        Mission salva = Mission.builder()
                .id(1L)
                .title("Nova Missão")
                .objectiveType("GANHAR_XP")
                .xpReward(100)
                .quantidade(3)
                .build();

        when(missionRepository.save(any(Mission.class))).thenReturn(salva);

        MissaoDto.Response response = adminMissaoService.criar(request);

        ArgumentCaptor<Mission> captor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(captor.capture());
        assertEquals("Nova Missão", captor.getValue().getTitle());
        assertEquals("GANHAR_XP", captor.getValue().getObjectiveType());
        assertEquals(100, captor.getValue().getXpReward());
        assertEquals(3, captor.getValue().getQuantidade());
        assertNotNull(response);
        assertEquals("Nova Missão", response.getTitle());
    }

    @Test
    void atualizarDeveAlterarCamposDaMissao() {
        Mission mission = missionBase();
        MissaoDto.Request request = MissaoDto.Request.builder()
                .title("Atualizada")
                .objectiveType("CONCLUIR_MODULO")
                .xpReward(250)
                .quantity(2)
                .build();

        when(missionRepository.findById(1L)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        MissaoDto.Response response = adminMissaoService.atualizar(1L, request);

        assertEquals("Atualizada", response.getTitle());
        assertEquals("CONCLUIR_MODULO", response.getObjectiveType());
        assertEquals(250, response.getXpReward());
        assertEquals(2, response.getQuantity());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoMissaoNaoExiste() {
        when(missionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminMissaoService.atualizar(99L, MissaoDto.Request.builder()
                        .title("X").objectiveType("Y").xpReward(1).quantity(1).build()));
    }

    @Test
    void deletarDeveChamarDeletePorIdQuandoMissaoExiste() {
        when(missionRepository.existsById(1L)).thenReturn(true);

        adminMissaoService.deletar(1L);

        verify(missionRepository).deleteById(1L);
    }

    @Test
    void deletarDeveLancarExcecaoQuandoMissaoNaoExiste() {
        when(missionRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> adminMissaoService.deletar(99L));
        verify(missionRepository, never()).deleteById(any());
    }

    private Mission missionBase() {
        return Mission.builder()
                .id(1L)
                .title("Resolva 5 exercícios")
                .objectiveType("RESOLVER_EXERCICIOS")
                .xpReward(50)
                .quantidade(5)
                .build();
    }
}
