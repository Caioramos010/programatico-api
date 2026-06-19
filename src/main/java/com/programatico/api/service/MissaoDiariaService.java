package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.UserDailyMissionRepository;
import com.programatico.api.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Motor de missões diárias. Sorteia 3 missões por dia (determinístico pela data, iguais pra todos),
 * acompanha o progresso por usuário/dia e premia com XP ao concluir. O conjunto "reseta" naturalmente
 * à meia-noite porque a chave é a data — um novo dia gera um novo conjunto de {@link UserDailyMission}.
 */
@Service
@RequiredArgsConstructor
public class MissaoDiariaService {

    public static final String EARN_XP = "EARN_XP";
    public static final String CORRECT_ANSWERS = "CORRECT_ANSWERS";
    public static final String COMPLETE_MODULES = "COMPLETE_MODULES";
    public static final String READ_PAGES = "READ_PAGES";
    public static final String PRACTICE_ERRORS = "PRACTICE_ERRORS";
    public static final String PERFECT_SESSION = "PERFECT_SESSION";

    private static final int MISSOES_POR_DIA = 3;

    private final MissionRepository missionRepository;
    private final UserDailyMissionRepository userDailyMissionRepository;
    private final UserStatsRepository userStatsRepository;

    /** Missões do dia do usuário; cria o conjunto se ainda não existir para hoje. */
    @Transactional
    public List<UserDailyMission> missoesDoDia(Usuario usuario) {
        LocalDate hoje = LocalDate.now();
        List<UserDailyMission> existentes = userDailyMissionRepository.findByUsuarioAndMissionDate(usuario, hoje);
        if (!existentes.isEmpty()) {
            return existentes;
        }

        List<Mission> catalogo = missionRepository.findAll().stream()
                .sorted(Comparator.comparing(Mission::getId))
                .toList();
        if (catalogo.isEmpty()) {
            return List.of();
        }

        List<UserDailyMission> criadas = new ArrayList<>();
        for (Mission m : selecionarDoDia(catalogo, hoje)) {
            criadas.add(userDailyMissionRepository.save(UserDailyMission.builder()
                    .usuario(usuario)
                    .mission(m)
                    .missionDate(hoje)
                    .currentProgress(0)
                    .goal(m.getQuantidade() != null ? m.getQuantidade() : 1)
                    .completed(false)
                    .build()));
        }
        return criadas;
    }

    /** Rotação determinística: o ponto de partida no catálogo desliza com a data. */
    private List<Mission> selecionarDoDia(List<Mission> catalogo, LocalDate hoje) {
        if (catalogo.size() <= MISSOES_POR_DIA) {
            return catalogo;
        }
        int offset = (int) Math.floorMod(hoje.toEpochDay(), catalogo.size());
        List<Mission> selecionadas = new ArrayList<>();
        for (int i = 0; i < MISSOES_POR_DIA; i++) {
            selecionadas.add(catalogo.get((offset + i) % catalogo.size()));
        }
        return selecionadas;
    }

    /**
     * Aplica incrementos às missões do dia conforme o objectiveType. Ao concluir uma missão,
     * credita o XP de recompensa no total do usuário. Retorna os títulos das missões recém-concluídas
     * (para o cliente exibir um toast).
     */
    @Transactional
    public List<String> registrarProgresso(Usuario usuario, Map<String, Integer> incrementosPorTipo) {
        if (incrementosPorTipo == null || incrementosPorTipo.isEmpty()) {
            return List.of();
        }

        List<String> concluidasAgora = new ArrayList<>();
        int xpBonus = 0;

        for (UserDailyMission udm : missoesDoDia(usuario)) {
            if (Boolean.TRUE.equals(udm.getCompleted())) {
                continue;
            }
            Integer inc = incrementosPorTipo.get(udm.getMission().getObjectiveType());
            if (inc == null || inc <= 0) {
                continue;
            }
            int novo = Math.min(udm.getGoal(), udm.getCurrentProgress() + inc);
            udm.setCurrentProgress(novo);
            if (novo >= udm.getGoal()) {
                udm.setCompleted(true);
                udm.setCompletedAt(LocalDateTime.now());
                concluidasAgora.add(udm.getMission().getTitle());
                xpBonus += udm.getMission().getXpReward() != null ? udm.getMission().getXpReward() : 0;
            }
            userDailyMissionRepository.save(udm);
        }

        if (xpBonus > 0) {
            final int bonus = xpBonus;
            userStatsRepository.findByUsuario(usuario).ifPresent(stats -> {
                creditarXp(stats, bonus);
                userStatsRepository.save(stats);
            });
        }
        return concluidasAgora;
    }

    private void creditarXp(UserStats stats, int xp) {
        stats.setTotalXp((stats.getTotalXp() != null ? stats.getTotalXp() : 0) + xp);
    }
}
