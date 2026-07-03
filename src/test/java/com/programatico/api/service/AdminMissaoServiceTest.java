package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.dto.MissaoDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.MissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMissaoServiceTest {

    @Mock private MissionRepository missionRepository;
    @InjectMocks private AdminMissaoService adminMissaoService;

    @Test
    void listarTodasDeveRetornarMissoes() {
        Mission mission = Mission.builder().id(1L).title("Complete 3").objectiveType("MODULES")
                .xpReward(10).quantidade(3).build();
        when(missionRepository.findAll()).thenReturn(List.of(mission));

        List<MissaoDto.Response> response = adminMissaoService.listarTodas();

        assertEquals(1, response.size());
        assertEquals("Complete 3", response.get(0).getTitle());
    }

    @Test
    void criarDevePersistirMissao() {
        MissaoDto.Request request = MissaoDto.Request.builder()
                .title("Nova missão")
                .objectiveType("XP")
                .xpReward(20)
                .quantity(100)
                .build();
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            m.setId(2L);
            return m;
        });

        MissaoDto.Response response = adminMissaoService.criar(request);

        assertEquals("Nova missão", response.getTitle());
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void deletarDeveFalharQuandoMissaoNaoExiste() {
        when(missionRepository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> adminMissaoService.deletar(99L));
    }
}
