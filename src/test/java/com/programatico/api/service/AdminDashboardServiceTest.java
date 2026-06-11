package com.programatico.api.service;

import com.programatico.api.dto.AdminDashboardDto;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private ModuloRepository moduloRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    void getMetricasDeveRetornarContagensECrescimento() {
        when(usuarioRepository.count()).thenReturn(110L);
        when(usuarioRepository.countByDataCriacaoAfter(any(Instant.class))).thenReturn(10L);
        when(practiceSessionRepository.countByEndedAtIsNullAndStartedAtAfter(any(LocalDateTime.class)))
                .thenReturn(3L);
        when(moduloRepository.count()).thenReturn(25L);

        AdminDashboardDto.Response response = adminDashboardService.getMetricas();

        assertEquals(110L, response.getTotalUsers());
        assertEquals(3L, response.getActiveSessions());
        assertEquals(25L, response.getTotalModules());
        // 10 novos sobre base anterior de 100 → 10%
        assertEquals(10, response.getGrowthPercent());
    }

    @Test
    void getMetricasDeveRetornarCemPorCentoQuandoTodosUsuariosSaoNovos() {
        when(usuarioRepository.count()).thenReturn(5L);
        when(usuarioRepository.countByDataCriacaoAfter(any(Instant.class))).thenReturn(5L);
        when(practiceSessionRepository.countByEndedAtIsNullAndStartedAtAfter(any(LocalDateTime.class)))
                .thenReturn(0L);
        when(moduloRepository.count()).thenReturn(0L);

        AdminDashboardDto.Response response = adminDashboardService.getMetricas();

        assertEquals(100, response.getGrowthPercent());
    }

    @Test
    void getMetricasDeveRetornarZeroCrescimentoSemUsuarios() {
        when(usuarioRepository.count()).thenReturn(0L);
        when(usuarioRepository.countByDataCriacaoAfter(any(Instant.class))).thenReturn(0L);
        when(practiceSessionRepository.countByEndedAtIsNullAndStartedAtAfter(any(LocalDateTime.class)))
                .thenReturn(0L);
        when(moduloRepository.count()).thenReturn(0L);

        AdminDashboardDto.Response response = adminDashboardService.getMetricas();

        assertEquals(0, response.getGrowthPercent());
        assertEquals(0L, response.getTotalUsers());
    }
}
