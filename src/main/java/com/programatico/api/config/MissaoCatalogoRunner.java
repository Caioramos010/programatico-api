package com.programatico.api.config;

import com.programatico.api.domain.Mission;
import com.programatico.api.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Garante o catálogo de missões diárias. Idempotente por objectiveType: insere só as que faltam,
 * sem sobrescrever missões já existentes (não roda atrás de flag — é só um "ensure").
 */
@Component
@Order(320)
@RequiredArgsConstructor
public class MissaoCatalogoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MissaoCatalogoRunner.class);

    private final MissionRepository missionRepository;

    private record Template(String title, String objectiveType, int xpReward, int quantidade) {}

    private static final List<Template> CATALOGO = List.of(
            new Template("Ganhe 30 XP hoje", "EARN_XP", 10, 30),
            new Template("Acerte 10 exercícios", "CORRECT_ANSWERS", 10, 10),
            new Template("Conclua 1 módulo", "COMPLETE_MODULES", 15, 1),
            new Template("Leia 1 teoria", "READ_PAGES", 10, 1),
            new Template("Pratique seus erros", "PRACTICE_ERRORS", 10, 1),
            new Template("Sessão sem errar", "PERFECT_SESSION", 15, 1)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existentes = missionRepository.findAll().stream()
                .map(Mission::getObjectiveType)
                .collect(Collectors.toSet());

        int criadas = 0;
        for (Template t : CATALOGO) {
            if (existentes.contains(t.objectiveType())) continue;
            missionRepository.save(Mission.builder()
                    .title(t.title())
                    .objectiveType(t.objectiveType())
                    .xpReward(t.xpReward())
                    .quantidade(t.quantidade())
                    .build());
            criadas++;
        }
        if (criadas > 0) {
            log.info("Catálogo de missões: {} missões criadas.", criadas);
        }
    }
}
