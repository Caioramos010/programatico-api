package com.programatico.api.config;

import com.programatico.api.domain.Track;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedContentRunnerTest {

    @Mock private TrackRepository trackRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private TeoriaPaginaRepository teoriaPaginaRepository;
    @Mock private ContentBlockRepository contentBlockRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private MissionRepository missionRepository;

    @InjectMocks private SeedContentRunner seedContentRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seedContentRunner, "enabled", false);
    }

    @Test
    void runDeveIgnorarQuandoDesabilitado() {
        seedContentRunner.run(new DefaultApplicationArguments());

        verify(trackRepository, never()).count();
    }

    @Test
    void runDeveIgnorarQuandoJaExisteTrilha() {
        ReflectionTestUtils.setField(seedContentRunner, "enabled", true);
        when(trackRepository.count()).thenReturn(1L);

        seedContentRunner.run(new DefaultApplicationArguments());

        verify(trackRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runDeveExecutarSeedQuandoHabilitadoESemTrilhas() {
        ReflectionTestUtils.setField(seedContentRunner, "enabled", true);
        when(trackRepository.count()).thenReturn(0L);
        when(trackRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            Track track = inv.getArgument(0);
            track.setId(1L);
            return track;
        });
        when(moduloRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(teoriaPaginaRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(contentBlockRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(exerciseRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(missionRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        seedContentRunner.run(new DefaultApplicationArguments());

        verify(trackRepository).save(org.mockito.ArgumentMatchers.any());
        verify(missionRepository, org.mockito.Mockito.atLeastOnce()).save(org.mockito.ArgumentMatchers.any());
    }
}
