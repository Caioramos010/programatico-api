package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.dto.TheoryDto;
import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
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

import java.time.LocalDateTime;
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
    private final TeoriaPaginaRepository teoriaPaginaRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final NotificationService notificationService;
    private final VidasService vidasService;

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

        List<TrackDto.ModuleWithProgress> modulesWithProgress = new ArrayList<>();
        // The module previous to the first is treated as "completed" to unlock the first module.
        boolean previousCompleted = true;

        for (Modulo modulo : modulos) {
            ProgressStatus statusDb = progressoMap.get(modulo.getId());
            ProgressStatus statusFinal;

            if (statusDb == ProgressStatus.COMPLETED) {
                statusFinal = ProgressStatus.COMPLETED;
            } else if (previousCompleted) {
                statusFinal = ProgressStatus.UNLOCKED;
            } else {
                statusFinal = ProgressStatus.LOCKED;
            }

            long xpModulo = "ACTIVITY".equals(modulo.getModuleType().name())
                    ? exerciseRepository.sumXpByModulo(modulo)
                    : 0L;
            modulesWithProgress.add(new TrackDto.ModuleWithProgress(
                    modulo.getId(),
                    modulo.getTitle(),
                    modulo.getModuleType().name(),
                    modulo.getDisplayOrder(),
                    statusFinal.name(),
                    modulo.getDescription(),
                    xpModulo
            ));

            previousCompleted = statusFinal == ProgressStatus.COMPLETED;
        }

        long completed = modulesWithProgress.stream()
                .filter(m -> "COMPLETED".equals(m.status()))
                .count();
        int percentage = modulos.isEmpty() ? 0 : (int) (completed * 100L / modulos.size());

        return TrackDto.Response.builder()
                .id(track.getId())
                .title(track.getTitle())
                .description(track.getDescription())
                .icon(track.getIcon())
                .modules(modulesWithProgress)
                .completedPercentage(percentage)
                .totalModules(modulos.size())
                .completedModules((int) completed)
                .build();
    }

    @Transactional
    public UserStatsDto.Response getEstatisticas(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        boolean vidasIlimitadas = vidasService.temVidasIlimitadas(usuario);

        return userStatsRepository.findByUsuario(usuario)
                .map(stats -> {
                    vidasService.aplicarRecarga(stats);
                    userStatsRepository.save(stats);
                    return UserStatsDto.Response.fromEntity(
                            stats, vidasService.segundosAteProximaVida(stats), vidasIlimitadas);
                })
                .orElseGet(() -> UserStatsDto.Response.padrao(vidasIlimitadas));
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
                            .title(mission.getTitle())
                            .type(mission.getObjectiveType())
                            .currentProgress(um != null && um.getCurrentProgress() != null ? um.getCurrentProgress() : 0)
                            .goal(META_MISSOES_DIARIAS)
                            .xpReward(mission.getXpReward() != null ? mission.getXpReward() : 5)
                            .completed(um != null && Boolean.TRUE.equals(um.getIsCompleted()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TheoryDto.Response getTeorico(Long moduleId, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        Modulo modulo = moduloRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo não encontrado."));

        if (modulo.getModuleType() != ModuleType.STUDY) {
            throw new BadRequestException("Este módulo não é teórico.");
        }

        ProgressStatus status = userProgressRepository.findByUsuarioAndModulo(usuario, modulo)
                .map(UserProgress::getStatus)
                .orElse(ProgressStatus.UNLOCKED);
        if (status == ProgressStatus.LOCKED) {
            throw new BadRequestException("Módulo bloqueado.");
        }

        List<TeoriaPagina> pages = teoriaPaginaRepository.findByModuloOrderByDisplayOrderAsc(modulo);

        List<TheoryDto.Page> pageDtos = pages.stream()
                .map(page -> {
                    List<ContentBlock> blocks = contentBlockRepository.findByPaginaOrderByDisplayOrderAsc(page);
                    List<TheoryDto.Block> blockDtos = blocks.stream()
                            .map(block -> TheoryDto.Block.builder()
                                    .id(block.getId())
                                    .layoutType(block.getLayoutType())
                                    .textContent(block.getTextContent())
                                    .imageUrl(block.getImageUrl())
                                    .order(block.getDisplayOrder() != null ? block.getDisplayOrder() : 0)
                                    .build())
                            .collect(Collectors.toList());
                    return TheoryDto.Page.builder()
                            .id(page.getId())
                            .title(page.getTitle())
                            .description(page.getDescription())
                            .order(page.getDisplayOrder() != null ? page.getDisplayOrder() : 0)
                            .blocks(blockDtos)
                            .build();
                })
                .collect(Collectors.toList());

        return TheoryDto.Response.builder()
                .moduleId(modulo.getId())
                .moduleTitle(modulo.getTitle())
                .pages(pageDtos)
                .build();
    }

    @Transactional
    public void concluirTeorico(Long moduleId, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        Modulo modulo = moduloRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo não encontrado."));

        if (modulo.getModuleType() != ModuleType.STUDY) {
            throw new BadRequestException("Este módulo não é teórico.");
        }

        UserProgress progress = userProgressRepository.findByUsuarioAndModulo(usuario, modulo)
                .orElseGet(() -> UserProgress.builder()
                        .usuario(usuario)
                        .modulo(modulo)
                        .build());

        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(LocalDateTime.now());
        userProgressRepository.save(progress);

        notificationService.criarNotificacaoSistema(
                usuario,
                "Módulo teórico concluído",
                "Voce concluiu o módulo teórico \"%s\".".formatted(modulo.getTitle()),
                NotificationKind.TRILHA
        );
    }
}
