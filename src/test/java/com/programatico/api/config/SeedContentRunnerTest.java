package com.programatico.api.config;

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
}
