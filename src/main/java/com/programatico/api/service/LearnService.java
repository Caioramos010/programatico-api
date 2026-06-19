package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
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
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ExerciseRepository exerciseRepository;
    private final TeoriaPaginaRepository teoriaPaginaRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final NotificationService notificationService;
    private final VidasService vidasService;
    private final MissaoDiariaService missaoDiariaService;
    private final PracticeSessionRepository practiceSessionRepository;

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
        boolean rootAtivo = vidasService.isRootAtivo(usuario);

        // Módulos com sessão aberta (em andamento) — para o botão "Continuar".
        Set<Long> emAndamentoIds = practiceSessionRepository.findByUsuarioAndEndedAtIsNull(usuario).stream()
                .filter(s -> s.getModulo() != null)
                .map(s -> s.getModulo().getId())
                .collect(Collectors.toSet());

        // Top assuntos (benefício Root): calcula uma vez por módulo de atividade.
        Map<Long, List<String>> topPorAtividade = new HashMap<>();
        if (rootAtivo) {
            for (Modulo m : modulos) {
                if ("ACTIVITY".equals(m.getModuleType().name())) {
                    topPorAtividade.put(m.getId(), calcularTopAssuntos(m, 3));
                }
            }
        }

        for (int idx = 0; idx < modulos.size(); idx++) {
            Modulo modulo = modulos.get(idx);
            ProgressStatus statusDb = progressoMap.get(modulo.getId());
            ProgressStatus statusFinal;

            if (statusDb == ProgressStatus.COMPLETED) {
                statusFinal = ProgressStatus.COMPLETED;
            } else if (previousCompleted) {
                statusFinal = ProgressStatus.UNLOCKED;
            } else {
                statusFinal = ProgressStatus.LOCKED;
            }

            // XP de UMA sessão (10 exercícios: 3×7 + 3×5 + 4×3 = 48), não a soma de todos os exercícios.
            boolean isActivity = "ACTIVITY".equals(modulo.getModuleType().name());
            long xpModulo = isActivity
                    ? Math.min(exerciseRepository.sumXpByModulo(modulo), 48L)
                    : 0L;
            // Atividade usa os próprios assuntos; teoria herda os da próxima atividade (condizente com os exercícios do tema).
            List<String> topAssuntos = List.of();
            if (rootAtivo) {
                if (isActivity) {
                    topAssuntos = topPorAtividade.getOrDefault(modulo.getId(), List.of());
                } else {
                    for (int j = idx + 1; j < modulos.size(); j++) {
                        if ("ACTIVITY".equals(modulos.get(j).getModuleType().name())) {
                            topAssuntos = topPorAtividade.getOrDefault(modulos.get(j).getId(), List.of());
                            break;
                        }
                    }
                }
            }
            modulesWithProgress.add(new TrackDto.ModuleWithProgress(
                    modulo.getId(),
                    modulo.getTitle(),
                    modulo.getModuleType().name(),
                    modulo.getDisplayOrder(),
                    statusFinal.name(),
                    modulo.getDescription(),
                    xpModulo,
                    topAssuntos,
                    emAndamentoIds.contains(modulo.getId())
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

    @Transactional
    public List<UserMissionDto.Response> getMissoes(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));

        return missaoDiariaService.missoesDoDia(usuario).stream()
                .map(udm -> {
                    Mission mission = udm.getMission();
                    return UserMissionDto.Response.builder()
                            .missionId(mission.getId())
                            .title(mission.getTitle())
                            .type(mission.getObjectiveType())
                            .currentProgress(udm.getCurrentProgress() != null ? udm.getCurrentProgress() : 0)
                            .goal(udm.getGoal() != null ? udm.getGoal() : META_MISSOES_DIARIAS)
                            .xpReward(mission.getXpReward() != null ? mission.getXpReward() : 5)
                            .completed(Boolean.TRUE.equals(udm.getCompleted()))
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
    public TheoryDto.ConclusaoResponse concluirTeorico(Long moduleId, String username) {
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

        boolean primeiraConclusao = progress.getStatus() != ProgressStatus.COMPLETED;

        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(LocalDateTime.now());
        userProgressRepository.save(progress);

        // Notifica só na primeira vez que o módulo teórico é concluído.
        if (primeiraConclusao) {
            notificationService.criarNotificacaoSistema(
                    usuario,
                    "Módulo teórico concluído",
                    "Voce concluiu o módulo teórico \"%s\".".formatted(modulo.getTitle()),
                    NotificationKind.TRILHA
            );
        }

        List<String> missoesConcluidas = missaoDiariaService.registrarProgresso(
                usuario, Map.of(MissaoDiariaService.READ_PAGES, 1));

        return TheoryDto.ConclusaoResponse.builder()
                .firstCompletion(primeiraConclusao)
                .completedMissions(missoesConcluidas)
                .build();
    }

    /** Assuntos (tags) que mais aparecem nos exercícios do módulo, do mais frequente para o menos. */
    private List<String> calcularTopAssuntos(Modulo modulo, int limite) {
        Map<String, Long> contagem = new LinkedHashMap<>();
        for (var exercise : exerciseRepository.findByModuloOrderByIdAsc(modulo)) {
            for (String tag : parseTags(exercise.getTags())) {
                contagem.merge(tag, 1L, Long::sum);
            }
        }
        return contagem.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
