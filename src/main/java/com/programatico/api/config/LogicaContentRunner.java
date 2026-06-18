package com.programatico.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Carrega a trilha "Lógica de Programação" a partir de TODOS os arquivos resources/seed/logica-*.json.
 *
 * O arquivo da trilha traz o nó "track"; os arquivos de tema trazem só "modules" (e se anexam à trilha
 * existente). Módulos STUDY trazem páginas com blocos (texto e imagem); ACTIVITY trazem exercícios.
 * Idempotente: trilha por título, cada módulo só é criado se ainda não existir. Atrás de SEED_LOGICA_ENABLED.
 */
@Component
@Order(310)
@RequiredArgsConstructor
public class LogicaContentRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LogicaContentRunner.class);
    private static final String TRILHA_TITULO = "Lógica de Programação";

    private final TrackRepository trackRepository;
    private final ModuloRepository moduloRepository;
    private final ExerciseRepository exerciseRepository;
    private final TeoriaPaginaRepository teoriaPaginaRepository;
    private final ContentBlockRepository contentBlockRepository;
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
        try {
            Resource[] recursos = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:seed/logica-*.json");
            // Ordem por nome de arquivo: 'logica-modulo-01' (com a trilha) antes de 'logica-tema-NN'.
            Arrays.sort(recursos, Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()));
            for (Resource r : recursos) {
                carregar(r);
            }
        } catch (Exception e) {
            log.error("Falha ao listar os seeds de lógica: {}", e.getMessage(), e);
        }
    }

    private void carregar(Resource recurso) {
        try (InputStream in = recurso.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            Track track = root.has("track")
                    ? obterOuCriarTrack(root.get("track"))
                    : trackPorTitulo(TRILHA_TITULO);
            if (track == null) {
                log.warn("Trilha '{}' não existe ainda — pulando {}.", TRILHA_TITULO, recurso.getFilename());
                return;
            }
            for (JsonNode m : root.get("modules")) {
                carregarModulo(track, m);
            }
        } catch (Exception e) {
            log.error("Falha ao carregar seed {}: {}", recurso.getFilename(), e.getMessage(), e);
        }
    }

    private void carregarModulo(Track track, JsonNode m) throws Exception {
        String titulo = m.get("title").asText();
        boolean existe = moduloRepository.findByTrackOrderByDisplayOrderAsc(track).stream()
                .anyMatch(x -> titulo.equals(x.getTitle()));
        if (existe) {
            log.info("Módulo '{}' já existe — pulando.", titulo);
            return;
        }

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title(titulo)
                .moduleType(ModuleType.valueOf(m.get("moduleType").asText()))
                .displayOrder(m.get("displayOrder").asInt())
                .description(m.hasNonNull("description") ? m.get("description").asText() : null)
                .build());

        if (m.has("pages")) {
            int ordemPagina = 1;
            int totalPaginas = 0;
            for (JsonNode p : m.get("pages")) {
                TeoriaPagina pagina = teoriaPaginaRepository.save(TeoriaPagina.builder()
                        .modulo(modulo)
                        .title(p.get("title").asText())
                        .description(p.hasNonNull("description") ? p.get("description").asText() : null)
                        .displayOrder(ordemPagina++)
                        .build());
                int ordemBloco = 1;
                for (JsonNode b : p.get("blocks")) {
                    String img = b.hasNonNull("image") ? "/img/exercicios/" + b.get("image").asText() : null;
                    contentBlockRepository.save(ContentBlock.builder()
                            .modulo(modulo)
                            .pagina(pagina)
                            .layoutType(LayoutType.valueOf(b.get("layout").asText()))
                            .textContent(b.hasNonNull("text") ? b.get("text").asText() : null)
                            .imageUrl(img)
                            .displayOrder(ordemBloco++)
                            .build());
                }
                totalPaginas++;
            }
            log.info("Módulo de teoria '{}' carregado com {} páginas.", titulo, totalPaginas);
        }

        if (m.has("exercises")) {
            int total = 0;
            for (JsonNode ex : m.get("exercises")) {
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
            log.info("Módulo de atividade '{}' carregado com {} exercícios.", titulo, total);
        }
    }

    private Track trackPorTitulo(String titulo) {
        return trackRepository.findAll().stream()
                .filter(t -> titulo.equals(t.getTitle()))
                .findFirst()
                .orElse(null);
    }

    private Track obterOuCriarTrack(JsonNode t) {
        String title = t.get("title").asText();
        Track existente = trackPorTitulo(title);
        if (existente != null) {
            return existente;
        }
        return trackRepository.save(Track.builder()
                .title(title)
                .description(t.get("description").asText())
                .displayOrder(t.get("displayOrder").asInt())
                .icon(null)
                .build());
    }
}
