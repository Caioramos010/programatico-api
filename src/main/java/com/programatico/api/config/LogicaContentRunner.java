package com.programatico.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Carrega a trilha "Lógica de Programação" a partir dos arquivos JSON em resources/seed/.
 *
 * Idempotente em dois níveis: a trilha é reaproveitada por título e cada módulo só é criado
 * se ainda não existir (por título) dentro da trilha — reruns não duplicam.
 * Atrás da flag SEED_LOGICA_ENABLED (independente do SeedContentRunner de exemplo).
 */
@Component
@Order(310)
@RequiredArgsConstructor
public class LogicaContentRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LogicaContentRunner.class);

    /** Arquivos de módulo a carregar, na ordem. Adicionar os próximos módulos aqui. */
    private static final List<String> ARQUIVOS = List.of(
            "seed/logica-modulo-01.json"
    );

    private final TrackRepository trackRepository;
    private final ModuloRepository moduloRepository;
    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper objectMapper;

    @Value("${seed.logica.enabled:false}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Seed de lógica desativado. Setar SEED_LOGICA_ENABLED=true pra carregar.");
            return;
        }
        for (String arquivo : ARQUIVOS) {
            carregar(arquivo);
        }
    }

    private void carregar(String arquivo) {
        try (InputStream in = new ClassPathResource(arquivo).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);

            Track track = obterOuCriarTrack(root.get("track"));

            JsonNode m = root.get("module");
            String moduloTitle = m.get("title").asText();
            boolean jaExiste = moduloRepository.findByTrackOrderByDisplayOrderAsc(track).stream()
                    .anyMatch(x -> moduloTitle.equals(x.getTitle()));
            if (jaExiste) {
                log.info("Módulo '{}' já existe — pulando ({}).", moduloTitle, arquivo);
                return;
            }

            Modulo modulo = moduloRepository.save(Modulo.builder()
                    .track(track)
                    .title(moduloTitle)
                    .moduleType(ModuleType.valueOf(m.get("moduleType").asText()))
                    .displayOrder(m.get("displayOrder").asInt())
                    .description(m.hasNonNull("description") ? m.get("description").asText() : null)
                    .build());

            int total = 0;
            for (JsonNode ex : root.get("exercises")) {
                String img = ex.hasNonNull("img") ? "/img/exercicios/" + ex.get("img").asText() : null;
                exerciseRepository.save(Exercise.builder()
                        .modulo(modulo)
                        .statement(ex.get("statement").asText())
                        .exerciseType(ExerciseType.valueOf(ex.get("type").asText()))
                        .exerciseData(objectMapper.writeValueAsString(ex.get("data")))
                        .xpReward(ex.get("xp").asInt())
                        .tags(ex.hasNonNull("tags") ? ex.get("tags").asText() : null)
                        .imageData(img)
                        .build());
                total++;
            }
            log.info("Módulo '{}' carregado com {} exercícios.", moduloTitle, total);
        } catch (Exception e) {
            log.error("Falha ao carregar seed de lógica {}: {}", arquivo, e.getMessage(), e);
        }
    }

    private Track obterOuCriarTrack(JsonNode t) {
        String title = t.get("title").asText();
        return trackRepository.findAll().stream()
                .filter(tr -> title.equals(tr.getTitle()))
                .findFirst()
                .orElseGet(() -> trackRepository.save(Track.builder()
                        .title(title)
                        .description(t.get("description").asText())
                        .displayOrder(t.get("displayOrder").asInt())
                        .icon(null)
                        .build()));
    }
}
