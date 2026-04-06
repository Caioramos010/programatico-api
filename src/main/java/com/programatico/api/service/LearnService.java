package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnService {

    private static final Logger log = LoggerFactory.getLogger(LearnService.class);
    private static final int META_MISSOES_DIARIAS = 3;

    private final UsuarioRepository usuarioRepository;
    private final TrackRepository trackRepository;
    private final ModuloRepository moduloRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStatsRepository userStatsRepository;
    private final MissionRepository missionRepository;
    private final UserMissionRepository userMissionRepository;
    private final ExerciseRepository exerciseRepository;

    /**
     * Retorna a primeira trilha (por displayOrder) com o status de cada módulo
     * calculado dinamicamente: um módulo só é UNLOCKED se o anterior foi COMPLETED.
     */
    @Transactional(readOnly = true)
    public TrackDto.Response getTrilhaComProgresso(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        Track track = trackRepository.findFirstByOrderByDisplayOrderAsc()
                .orElseThrow(() -> new BadRequestException("Nenhuma trilha cadastrada."));

        List<Modulo> modulos = moduloRepository.findByTrackOrderByDisplayOrderAsc(track);

        Map<Long, ProgressStatus> progressoMap = modulos.isEmpty()
                ? Map.of()
                : userProgressRepository.findByUsuarioAndModuloIn(usuario, modulos)
                        .stream()
                        .collect(Collectors.toMap(up -> up.getModulo().getId(), UserProgress::getStatus));

        List<TrackDto.ModuloComProgresso> modulosComProgresso = new ArrayList<>();
        // O módulo anterior ao primeiro é tratado como "concluído" para desbloquear o primeiro módulo.
        boolean anteriorConcluido = true;

        for (Modulo modulo : modulos) {
            ProgressStatus statusDb = progressoMap.get(modulo.getId());
            ProgressStatus statusFinal;

            if (statusDb == ProgressStatus.COMPLETED) {
                statusFinal = ProgressStatus.COMPLETED;
            } else if (anteriorConcluido) {
                statusFinal = ProgressStatus.UNLOCKED;
            } else {
                statusFinal = ProgressStatus.LOCKED;
            }

            long xpModulo = "ACTIVITY".equals(modulo.getModuleType().name())
                    ? exerciseRepository.sumXpByModulo(modulo)
                    : 0L;
            modulosComProgresso.add(new TrackDto.ModuloComProgresso(
                    modulo.getId(),
                    modulo.getTitle(),
                    modulo.getModuleType().name(),
                    modulo.getDisplayOrder(),
                    statusFinal.name(),
                    modulo.getDescription(),
                    xpModulo
            ));

            anteriorConcluido = statusFinal == ProgressStatus.COMPLETED;
        }

        long concluidos = modulosComProgresso.stream()
                .filter(m -> "COMPLETED".equals(m.status()))
                .count();
        int percentual = modulos.isEmpty() ? 0 : (int) (concluidos * 100L / modulos.size());

        return TrackDto.Response.builder()
                .id(track.getId())
                .titulo(track.getTitle())
                .descricao(track.getDescription())
                .icon(track.getIcon())
                .modulos(modulosComProgresso)
                .percentualConcluido(percentual)
                .totalModulos(modulos.size())
                .concluidosModulos((int) concluidos)
                .build();
    }

    @Transactional(readOnly = true)
    public UserStatsDto.Response getEstatisticas(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        return userStatsRepository.findByUsuario(usuario)
                .map(UserStatsDto.Response::fromEntity)
                .orElseGet(UserStatsDto.Response::padrao);
    }

    @Transactional(readOnly = true)
    public List<UserMissionDto.Response> getMissoes(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        List<Mission> missions = missionRepository.findAll().stream()
                .limit(3)
                .collect(Collectors.toList());

        Map<Long, UserMission> userMissionMap = userMissionRepository.findByUsuario(usuario)
                .stream()
                .collect(Collectors.toMap(um -> um.getMission().getId(), um -> um, (a, b) -> a));

        return missions.stream()
                .map(mission -> {
                    UserMission um = userMissionMap.get(mission.getId());
                    return UserMissionDto.Response.builder()
                            .missionId(mission.getId())
                            .titulo(mission.getTitle())
                            .tipo(mission.getObjectiveType())
                            .progressoAtual(um != null && um.getCurrentProgress() != null ? um.getCurrentProgress() : 0)
                            .meta(META_MISSOES_DIARIAS)
                            .recompensaXp(mission.getXpReward() != null ? mission.getXpReward() : 5)
                            .concluida(um != null && Boolean.TRUE.equals(um.getIsCompleted()))
                            .build();
                })
                .collect(Collectors.toList());
    }
}
