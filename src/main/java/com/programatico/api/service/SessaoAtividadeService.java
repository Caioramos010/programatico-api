package com.programatico.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.dto.SessaoDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessaoAtividadeService {

    private static final Logger log = LoggerFactory.getLogger(SessaoAtividadeService.class);
    private static final int QUANTIDADE_EXERCICIOS = 10;
    private static final int QUANTIDADE_XP_7 = 3;
    private static final int QUANTIDADE_XP_5 = 3;
    private static final int QUANTIDADE_XP_3 = 4;
    private static final int MAX_VIDAS = 5;

    private final UsuarioRepository usuarioRepository;
    private final ModuloRepository moduloRepository;
    private final ExerciseRepository exerciseRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStatsRepository userStatsRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SessaoDto.InicioResponse iniciarSessao(Long moduloId, String username) {
        Usuario usuario = buscarUsuario(username);
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));

        List<Exercise> selecionados = selecionarExercicios(modulo);
        if (selecionados.isEmpty()) {
            throw new BadRequestException("Este módulo não possui exercícios cadastrados.");
        }

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());
        if (stats.getId() == null) {
            userStatsRepository.save(stats);
        }

        PracticeSession sessao = PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .build();
        practiceSessionRepository.save(sessao);

        List<PracticeSessionExercise> sessionExercises = new ArrayList<>();
        for (int i = 0; i < selecionados.size(); i++) {
            sessionExercises.add(PracticeSessionExercise.builder()
                    .practiceSession(sessao)
                    .exercise(selecionados.get(i))
                    .displayOrder(i + 1)
                    .build());
        }
        practiceSessionExerciseRepository.saveAll(sessionExercises);

        List<SessaoDto.ExercicioSessao> exerciciosDtos = selecionados.stream()
                .map(ex -> toExercicioSessao(ex, selecionados.indexOf(ex) + 1))
                .collect(Collectors.toList());

        return SessaoDto.InicioResponse.builder()
                .sessaoId(sessao.getId())
                .tituloModulo(modulo.getTitle())
                .vidasIniciais(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .totalExercicios(exerciciosDtos.size())
                .exercicios(exerciciosDtos)
                .build();
    }

    @Transactional
    public SessaoDto.RespostaResponse responder(Long sessaoId, SessaoDto.RespostaRequest request, String username) {
        Usuario usuario = buscarUsuario(username);
        PracticeSession sessao = practiceSessionRepository.findByIdAndUsuario(sessaoId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão", sessaoId));

        if (sessao.getEndedAt() != null) {
            throw new BadRequestException("Esta sessão já foi encerrada.");
        }

        PracticeSessionExercise sessionExercise = practiceSessionExerciseRepository
                .findByPracticeSessionAndExerciseId(sessao, request.getExercicioId())
                .orElseThrow(() -> new BadRequestException("Exercício não pertence a esta sessão."));

        if (Boolean.TRUE.equals(sessionExercise.getIsCorrect()) || Boolean.FALSE.equals(sessionExercise.getIsCorrect())) {
            throw new BadRequestException("Este exercício já foi respondido.");
        }

        Exercise exercise = sessionExercise.getExercise();
        boolean correto = validarResposta(exercise, request.getResposta());
        String respostaCorreta = extrairRespostaCorreta(exercise);

        // Para PAIRS: resposta parcial correta — retorna sem marcar como respondido
        if (exercise.getExerciseType() == ExerciseType.PAIRS && correto) {
            try {
                Map<String, Object> pairsData = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<?> pairsCorretos = (List<?>) pairsData.get("pairs");
                List<?> enviados = objectMapper.readValue(request.getResposta(), new TypeReference<>() {});
                if (enviados.size() < pairsCorretos.size()) {
                    UserStats statsPartial = userStatsRepository.findByUsuario(usuario)
                            .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                                    .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());
                    return SessaoDto.RespostaResponse.builder()
                            .correto(true)
                            .respostaCorreta("")
                            .vidasRestantes(statsPartial.getCurrentLives() != null ? statsPartial.getCurrentLives() : MAX_VIDAS)
                            .assuntosRelacionados(List.of())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Erro ao verificar completude de PAIRS id={}: {}", exercise.getId(), e.getMessage());
            }
        }

        sessionExercise.setUserAnswer(request.getResposta());
        sessionExercise.setIsCorrect(correto);
        practiceSessionExerciseRepository.save(sessionExercise);

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());

        if (correto) {
            int xpAtual = stats.getTotalXp() != null ? stats.getTotalXp() : 0;
            stats.setTotalXp(xpAtual + exercise.getXpReward());
        } else {
            int vidasAtuais = stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS;
            stats.setCurrentLives(Math.max(0, vidasAtuais - 1));
        }
        stats.setLastActivityDate(LocalDateTime.now());
        userStatsRepository.save(stats);

        return SessaoDto.RespostaResponse.builder()
                .correto(correto)
                .respostaCorreta(respostaCorreta)
                .vidasRestantes(stats.getCurrentLives())
                .assuntosRelacionados(parseTags(exercise.getTags()))
                .build();
    }

    @Transactional
    public SessaoDto.ConclusaoResponse concluir(Long sessaoId, String username) {
        Usuario usuario = buscarUsuario(username);
        PracticeSession sessao = practiceSessionRepository.findByIdAndUsuario(sessaoId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão", sessaoId));

        if (sessao.getEndedAt() != null) {
            throw new BadRequestException("Esta sessão já foi encerrada.");
        }

        sessao.setEndedAt(LocalDateTime.now());
        practiceSessionRepository.save(sessao);

        List<PracticeSessionExercise> todos = practiceSessionExerciseRepository
                .findByPracticeSessionOrderByDisplayOrderAsc(sessao);

        long corretos = todos.stream().filter(e -> Boolean.TRUE.equals(e.getIsCorrect())).count();
        int taxaAcerto = todos.isEmpty() ? 0 : (int) (corretos * 100L / todos.size());
        int xpGanho = todos.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsCorrect()))
                .mapToInt(e -> e.getExercise().getXpReward())
                .sum();
        int duracao = (int) ChronoUnit.SECONDS.between(sessao.getStartedAt(), sessao.getEndedAt());

        // Marca o módulo como concluído se taxa de acerto >= 50%
        boolean moduloConcluido = false;
        if (sessao.getModulo() != null && taxaAcerto >= 50) {
            UserProgress progresso = userProgressRepository
                    .findByUsuarioAndModulo(usuario, sessao.getModulo())
                    .orElseGet(() -> UserProgress.builder()
                            .usuario(usuario)
                            .modulo(sessao.getModulo())
                            .build());
            progresso.setStatus(ProgressStatus.COMPLETED);
            progresso.setCompletedAt(LocalDateTime.now());
            userProgressRepository.save(progresso);
            moduloConcluido = true;
            log.info("Módulo id={} concluído pelo usuário={}", sessao.getModulo().getId(), username);
        }

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());

        return SessaoDto.ConclusaoResponse.builder()
                .xpGanho(xpGanho)
                .taxaAcerto(taxaAcerto)
                .duracaoSegundos(duracao)
                .vidasRestantes(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .moduloConcluido(moduloConcluido)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));
    }

    /**
     * Seleciona 10 exercícios: 3 com XP=7, 3 com XP=5, 4 com XP=3.
     * Preenche deficits com exercícios de outras faixas quando necessário.
     * Embaralha garantindo que não haja dois tipos iguais consecutivos.
     */
    private List<Exercise> selecionarExercicios(Modulo modulo) {
        List<Exercise> tier7 = shuffleList(new ArrayList<>(exerciseRepository.findByModuloAndXpReward(modulo, 7)));
        List<Exercise> tier5 = shuffleList(new ArrayList<>(exerciseRepository.findByModuloAndXpReward(modulo, 5)));
        List<Exercise> tier3 = shuffleList(new ArrayList<>(exerciseRepository.findByModuloAndXpReward(modulo, 3)));

        List<Exercise> selecionados = new ArrayList<>();
        selecionados.addAll(tier7.stream().limit(QUANTIDADE_XP_7).toList());
        selecionados.addAll(tier5.stream().limit(QUANTIDADE_XP_5).toList());
        selecionados.addAll(tier3.stream().limit(QUANTIDADE_XP_3).toList());

        // Preenche com exercícios de qualquer tier se não houver suficientes
        if (selecionados.size() < QUANTIDADE_EXERCICIOS) {
            List<Exercise> todos = new ArrayList<>(exerciseRepository.findByModuloOrderByIdAsc(modulo));
            todos.removeAll(selecionados);
            shuffleList(todos);
            int faltam = QUANTIDADE_EXERCICIOS - selecionados.size();
            selecionados.addAll(todos.stream().limit(faltam).toList());
        }

        return embaralharSemTiposConsecutivos(selecionados);
    }

    /**
     * Embaralha a lista garantindo que dois exercícios do mesmo tipo não sejam consecutivos.
     * Usa abordagem greedy: após embaralhar, percorre a lista e troca conflitos.
     */
    private List<Exercise> embaralharSemTiposConsecutivos(List<Exercise> lista) {
        if (lista.size() < 2) return lista;
        List<Exercise> resultado = new ArrayList<>(lista);
        Collections.shuffle(resultado);
        for (int i = 1; i < resultado.size(); i++) {
            if (resultado.get(i).getExerciseType() == resultado.get(i - 1).getExerciseType()) {
                for (int j = i + 1; j < resultado.size(); j++) {
                    if (resultado.get(j).getExerciseType() != resultado.get(i - 1).getExerciseType()) {
                        Collections.swap(resultado, i, j);
                        break;
                    }
                }
            }
        }
        return resultado;
    }

    private <T> List<T> shuffleList(List<T> list) {
        Collections.shuffle(list);
        return list;
    }

    /**
     * Converte um Exercise para DTO de exibição.
     * Para DRAG_DROP, embaralha os itens antes de enviar ao frontend.
     * Para PAIRS, embaralha a coluna direita.
     */
    private SessaoDto.ExercicioSessao toExercicioSessao(Exercise exercise, int ordem) {
        String dadosExibicao = prepararDadosExibicao(exercise);

        return SessaoDto.ExercicioSessao.builder()
                .id(exercise.getId())
                .ordem(ordem)
                .enunciado(exercise.getStatement())
                .tipo(exercise.getExerciseType().name())
                .dadosExibicao(dadosExibicao)
                .xpRecompensa(exercise.getXpReward())
                .assuntosRelacionados(parseTags(exercise.getTags()))
                .imagemData(exercise.getImageData())
                .build();
    }

    /**
     * Para DRAG_DROP, retorna os itens embaralhados (a ordem correta é o exerciseData original).
     * Para PAIRS, retorna os pares com a coluna direita embaralhada.
     * Para MULTIPLE_CHOICE, retorna as opções sem indicar qual é correta.
     */
    @SuppressWarnings("unchecked")
    private String prepararDadosExibicao(Exercise exercise) {
        try {
            if (exercise.getExerciseType() == ExerciseType.DRAG_DROP) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<String> items = (List<String>) data.get("items");
                List<String> embaralhados = new ArrayList<>(items);
                Collections.shuffle(embaralhados);
                return objectMapper.writeValueAsString(Map.of("items", embaralhados));
            }
            if (exercise.getExerciseType() == ExerciseType.PAIRS) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, String>> pairs = (List<Map<String, String>>) data.get("pairs");
                List<String> lefts = new ArrayList<>(pairs.stream().map(p -> p.get("left")).collect(Collectors.toList()));
                List<String> rights = new ArrayList<>(pairs.stream().map(p -> p.get("right")).collect(Collectors.toList()));
                Collections.shuffle(lefts);
                Collections.shuffle(rights);
                return objectMapper.writeValueAsString(Map.of("lefts", lefts, "rights", rights));
            }
            if (exercise.getExerciseType() == ExerciseType.MULTIPLE_CHOICE) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, Object>> options = (List<Map<String, Object>>) data.get("options");
                // Remove 'correct' flag from display data to avoid leaking the answer
                List<Map<String, Object>> displayOptions = options.stream().map(opt -> {
                    Map<String, Object> clean = new LinkedHashMap<>(opt);
                    clean.remove("correct");
                    return clean;
                }).collect(Collectors.toList());
                return objectMapper.writeValueAsString(Map.of("options", displayOptions));
            }
        } catch (Exception e) {
            log.warn("Erro ao preparar dados de exibição para exercício id={}: {}", exercise.getId(), e.getMessage());
        }
        return exercise.getExerciseData();
    }

    /**
     * Valida a resposta do usuário comparando com os dados originais do exercício.
     */
    @SuppressWarnings("unchecked")
    private boolean validarResposta(Exercise exercise, String respostaJson) {
        try {
            if (exercise.getExerciseType() == ExerciseType.MULTIPLE_CHOICE) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, Object>> options = (List<Map<String, Object>>) data.get("options");
                String descricaoCorreta = options.stream()
                        .filter(o -> Boolean.TRUE.equals(o.get("correct")))
                        .map(o -> String.valueOf(o.get("description")))
                        .findFirst().orElse("");
                String respostaLimpa = respostaJson.replace("\"", "").trim();
                return descricaoCorreta.equalsIgnoreCase(respostaLimpa);
            }

            if (exercise.getExerciseType() == ExerciseType.DRAG_DROP) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<String> ordemCorreta = (List<String>) data.get("items");
                List<String> respostaUsuario = objectMapper.readValue(respostaJson, new TypeReference<>() {});
                return ordemCorreta.equals(respostaUsuario);
            }

            if (exercise.getExerciseType() == ExerciseType.PAIRS) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, String>> pairsCorretos = (List<Map<String, String>>) data.get("pairs");
                List<Map<String, String>> respostaUsuario = objectMapper.readValue(respostaJson, new TypeReference<>() {});
                // Validação por subconjunto: cada par enviado deve estar nos pares corretos
                return respostaUsuario.stream().allMatch(par ->
                        pairsCorretos.stream().anyMatch(correto ->
                                correto.get("left").equalsIgnoreCase(par.get("left")) &&
                                correto.get("right").equalsIgnoreCase(par.get("right"))
                        )
                );
            }
        } catch (Exception e) {
            log.warn("Erro ao validar resposta do exercício id={}: {}", exercise.getId(), e.getMessage());
        }
        return false;
    }

    /**
     * Extrai a resposta correta do exercício para exibição no feedback de erro.
     */
    @SuppressWarnings("unchecked")
    private String extrairRespostaCorreta(Exercise exercise) {
        try {
            if (exercise.getExerciseType() == ExerciseType.MULTIPLE_CHOICE) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, Object>> options = (List<Map<String, Object>>) data.get("options");
                return options.stream()
                        .filter(o -> Boolean.TRUE.equals(o.get("correct")))
                        .map(o -> String.valueOf(o.get("description")))
                        .findFirst().orElse("");
            }
            if (exercise.getExerciseType() == ExerciseType.DRAG_DROP) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<String> items = (List<String>) data.get("items");
                return objectMapper.writeValueAsString(items);
            }
            if (exercise.getExerciseType() == ExerciseType.PAIRS) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, String>> pairs = (List<Map<String, String>>) data.get("pairs");
                return objectMapper.writeValueAsString(pairs);
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair resposta correta do exercício id={}: {}", exercise.getId(), e.getMessage());
        }
        return "";
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        try {
            return objectMapper.readValue(tags, new TypeReference<>() {});
        } catch (Exception e) {
            // Fallback: trata como CSV
            return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }
}
