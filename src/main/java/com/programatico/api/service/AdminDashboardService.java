package com.programatico.api.service;

import com.programatico.api.dto.AdminDashboardDto;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    /** Sessão é considerada ativa se começou há menos de 30 min e ainda não terminou. */
    private static final Duration JANELA_SESSAO_ATIVA = Duration.ofMinutes(30);
    private static final int DIAS_CRESCIMENTO = 30;

    private final UsuarioRepository usuarioRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final ModuloRepository moduloRepository;

    @Transactional(readOnly = true)
    public AdminDashboardDto.Response getMetricas() {
        long totalUsers = usuarioRepository.count();
        long activeSessions = practiceSessionRepository
                .countByEndedAtIsNullAndStartedAtAfter(LocalDateTime.now().minus(JANELA_SESSAO_ATIVA));
        long totalModules = moduloRepository.count();

        long novosUsuarios = usuarioRepository
                .countByDataCriacaoAfter(Instant.now().minus(DIAS_CRESCIMENTO, ChronoUnit.DAYS));
        long baseAnterior = totalUsers - novosUsuarios;
        int growthPercent;
        if (baseAnterior > 0) {
            growthPercent = (int) (novosUsuarios * 100 / baseAnterior);
        } else {
            growthPercent = novosUsuarios > 0 ? 100 : 0;
        }

        return AdminDashboardDto.Response.builder()
                .totalUsers(totalUsers)
                .activeSessions(activeSessions)
                .totalModules(totalModules)
                .growthPercent(growthPercent)
                .build();
    }
}
