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
import com.programatico.api.domain.enums.NotificationKind;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessaoAtividadeService {

    private static final Logger log = LoggerFactory.getLogger(SessaoAtividadeService.class);
    private static final int QUANTIDADE_EXERCICIOS = 10;
    private static final int QUANTIDADE_EXERCICIOS_FIXACAO = 5;
    private static final int QUANTIDADE_XP_7 = 3;
    private static final int QUANTIDADE_XP_5 = 3;
    private static final int QUANTIDADE_XP_3 = 4;
    private static final int MAX_VIDAS = VidasService.MAX_VIDAS;

    private final UsuarioRepository usuarioRepository;
    private final ModuloRepository moduloRepository;
    private final ExerciseRepository exerciseRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStatsRepository userStatsRepository;
    private final NotificationService notificationService;
    private final VidasService vidasService;
    private final ObjectMapper objectMapper;
    private final OpenAiOrganizacaoService openAiOrganizacaoService;
    private final MissaoDiariaService missaoDiariaService;

    @Transactional
    public SessaoDto.InicioResponse iniciarSessao(Long moduloId, String username) {
        Usuario usuario = buscarUsuario(username);
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());
        vidasService.aplicarRecarga(stats);
        userStatsRepository.save(stats);

        // Retoma a sessão aberta deste módulo (usuário fechou a atividade/site no meio).
        Optional<PracticeSession> aberta = practiceSessionRepository
                .findFirstByUsuarioAndModuloAndEndedAtIsNullOrderByStartedAtDesc(usuario, modulo);
        if (aberta.isPresent()) {
            List<PracticeSessionExercise> itens = practiceSessionExerciseRepository
                    .findByPracticeSessionOrderByDisplayOrderAsc(aberta.get());
            long pendentes = itens.stream().filter(i -> !Boolean.TRUE.equals(i.getMastered())).count();
            if (!itens.isEmpty() && pendentes > 0) {
                return montarResume(aberta.get(), itens, stats);
            }
            // Sessão aberta mas tudo dominado (ou vazia): encerra e segue criando uma nova.
            aberta.get().setEndedAt(LocalDateTime.now());
            practiceSessionRepository.save(aberta.get());
        }

        List<Exercise> selecionados = organizarExercicios(modulo, usuario, stats);
        if (selecionados.isEmpty()) {
            throw new BadRequestException("Este módulo não possui exercícios cadastrados.");
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
                .map(ex -> toExercicioSessao(ex, selecionados.indexOf(ex) + 1, SessionType.ACTIVITY))
                .collect(Collectors.toList());

        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(modulo.getTitle())
                .initialLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .totalExercises(exerciciosDtos.size())
                .exercises(exerciciosDtos)
                .build();
    }

    /** Retoma uma sessão aberta: devolve os alvos ainda NÃO dominados (a fila restante de maestria). */
    private SessaoDto.InicioResponse montarResume(PracticeSession sessao,
            List<PracticeSessionExercise> itens, UserStats stats) {
        List<PracticeSessionExercise> pendentes = itens.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getMastered()))
                .collect(Collectors.toList());
        List<SessaoDto.ExercicioSessao> dtos = new ArrayList<>();
        for (int i = 0; i < pendentes.size(); i++) {
            dtos.add(toExercicioSessao(pendentes.get(i).getExercise(), i + 1, sessao.getSessionType()));
        }
        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(sessao.getModulo() != null ? sessao.getModulo().getTitle() : null)
                .initialLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .totalExercises(dtos.size())
                .resumedFrom(0)
                .exercises(dtos)
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

        // Alvo da sessão (PSE) ou exercício de reforço (fora da sessão, na maestria).
        PracticeSessionExercise sessionExercise = practiceSessionExerciseRepository
                .findByPracticeSessionAndExerciseId(sessao, request.getExercicioId())
                .orElse(null);
        Exercise exercise = sessionExercise != null
                ? sessionExercise.getExercise()
                : exerciseRepository.findById(request.getExercicioId())
                        .orElseThrow(() -> new BadRequestException("Exercício não encontrado."));

        boolean primeiraTentativa = sessionExercise != null && sessionExercise.getIsCorrect() == null;
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
                            .correct(true)
                            .correctAnswer("")
                            .remainingLives(statsPartial.getCurrentLives() != null ? statsPartial.getCurrentLives() : MAX_VIDAS)
                            .relatedTopics(List.of())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Erro ao verificar completude de PAIRS id={}: {}", exercise.getId(), e.getMessage());
            }
        }

        if (sessionExercise != null) {
            sessionExercise.setUserAnswer(request.getResposta());
            if (primeiraTentativa) {
                sessionExercise.setIsCorrect(correto); // 1ª tentativa define a acurácia
            }
            if (correto) {
                sessionExercise.setMastered(true); // dominou (qualquer tentativa)
            }
            practiceSessionExerciseRepository.save(sessionExercise);
        }

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());
        vidasService.aplicarRecarga(stats);

        if (correto) {
            // XP só na 1ª tentativa de um alvo (sem farm), fora de replay.
            if (primeiraTentativa && !moduloJaConcluido(usuario, sessao)) {
                int xpAtual = stats.getTotalXp() != null ? stats.getTotalXp() : 0;
                stats.setTotalXp(xpAtual + exercise.getXpReward());
            }
        } else if (!vidasService.temVidasIlimitadas(usuario)) {
            vidasService.registrarPerdaDeVida(stats);
            if (stats.getCurrentLives() != null && stats.getCurrentLives() == 0) {
                notificationService.criarNotificacaoSistema(
                        usuario,
                        "Sem vidas",
                        "Você ficou sem vidas. Aguarde a recarga para continuar estudando.",
                        NotificationKind.EXERCICIO
                );
            }
        }
        stats.setLastActivityDate(LocalDateTime.now());
        userStatsRepository.save(stats);

        return SessaoDto.RespostaResponse.builder()
                .correct(correto)
                .correctAnswer(respostaCorreta)
                .remainingLives(stats.getCurrentLives())
                .relatedTopics(parseTags(exercise.getTags()))
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
        // Replay de módulo já concluído não pontuou em responder(); o relatório reflete isso.
        boolean replay = moduloJaConcluido(usuario, sessao);
        int xpGanho = replay ? 0 : todos.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsCorrect()))
                .mapToInt(e -> e.getExercise().getXpReward())
                .sum();
        int duracao = (int) ChronoUnit.SECONDS.between(sessao.getStartedAt(), sessao.getEndedAt());

        // Maestria: o módulo conclui quando TODOS os alvos foram dominados (acertados em alguma tentativa).
        boolean todosDominados = !todos.isEmpty()
                && todos.stream().allMatch(e -> Boolean.TRUE.equals(e.getMastered()));
        boolean moduloConcluido = false;
        if (sessao.getModulo() != null && todosDominados) {
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
        vidasService.aplicarRecarga(stats);
        userStatsRepository.save(stats);

        // Notifica só na PRIMEIRA conclusão do módulo (não em replays nem em sessões que não concluem).
        boolean primeiraConclusao = moduloConcluido && !replay;
        if (sessao.getModulo() != null && primeiraConclusao) {
            notificationService.criarNotificacaoSistema(
                    usuario,
                    "Módulo concluído",
                    "Voce concluiu o módulo \"%s\" pela primeira vez com %d%% de acerto.".formatted(
                            sessao.getModulo().getTitle(),
                            taxaAcerto
                    ),
                    NotificationKind.TRILHA
            );
        }

        // Progresso das missões diárias. EARN_XP usa só XP real (replay = 0, sem farm).
        Map<String, Integer> incMissoes = new LinkedHashMap<>();
        if (xpGanho > 0) incMissoes.put(MissaoDiariaService.EARN_XP, xpGanho);
        if (corretos > 0) incMissoes.put(MissaoDiariaService.CORRECT_ANSWERS, (int) corretos);
        if (moduloConcluido) incMissoes.put(MissaoDiariaService.COMPLETE_MODULES, 1);
        if (!todos.isEmpty() && taxaAcerto == 100) incMissoes.put(MissaoDiariaService.PERFECT_SESSION, 1);
        if (sessao.getSessionType() == SessionType.ERRORS) incMissoes.put(MissaoDiariaService.PRACTICE_ERRORS, 1);
        List<String> missoesConcluidas = missaoDiariaService.registrarProgresso(usuario, incMissoes);

        return SessaoDto.ConclusaoResponse.builder()
                .xpEarned(xpGanho)
                .accuracy(taxaAcerto)
                .durationSeconds(duracao)
                .remainingLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .moduleCompleted(moduloConcluido)
                .firstCompletion(primeiraConclusao)
                .completedMissions(missoesConcluidas)
                .build();
    }

    /** Maestria: ao errar, busca um exercício de reforço do mesmo módulo com assunto (tag) em comum. */
    @Transactional(readOnly = true)
    public SessaoDto.ExercicioSessao buscarReforco(Long sessaoId, Long exercicioId,
            List<Long> excluir, String username) {
        Usuario usuario = buscarUsuario(username);
        PracticeSession sessao = practiceSessionRepository.findByIdAndUsuario(sessaoId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão", sessaoId));
        if (sessao.getModulo() == null) {
            return null; // práticas (sem módulo) não têm reforço por tema
        }
        Exercise base = exerciseRepository.findById(exercicioId)
                .orElseThrow(() -> new BadRequestException("Exercício não encontrado."));
        Set<String> tagsBase = new HashSet<>(parseTags(base.getTags()));
        if (tagsBase.isEmpty()) {
            return null;
        }
        Set<Long> excluirSet = new HashSet<>(excluir != null ? excluir : List.of());
        excluirSet.add(exercicioId);

        List<Exercise> candidatos = exerciseRepository.findByModuloOrderByIdAsc(sessao.getModulo()).stream()
                .filter(e -> !excluirSet.contains(e.getId()))
                .filter(e -> parseTags(e.getTags()).stream().anyMatch(tagsBase::contains))
                .collect(Collectors.toList());
        if (candidatos.isEmpty()) {
            return null;
        }
        Collections.shuffle(candidatos);
        return toExercicioSessao(candidatos.get(0), 0, sessao.getSessionType());
    }

    // ── Práticas (esqueleto para Hyorran) ───────────────────────────────────────
    // O fluxo responder()/concluir() já é agnóstico de módulo (todos os acessos a
    // sessao.getModulo() têm guard de null), então cada modo de prática só precisa de
    // um "iniciar" próprio que produza um InicioResponse.

    @Transactional
    public SessaoDto.InicioResponse iniciarPratica(String modo, String username) {
        Usuario usuario = buscarUsuario(username);
        return switch (modo == null ? "" : modo.toLowerCase()) {
            case "erros" -> iniciarPraticaErros(usuario);
            case "fixacao" -> iniciarPraticaFixacao(usuario);
            case "cronometrado" -> iniciarPraticaCronometrada(usuario);
            default -> throw new BadRequestException("Modo de prática inválido: " + modo);
        };
    }

    /** Referência: pratica os exercícios que o usuário errou. */
    private SessaoDto.InicioResponse iniciarPraticaErros(Usuario usuario) {
        List<Exercise> exercicios = practiceSessionExerciseRepository
                .findExerciciosErradosDoUsuario(usuario)
                .stream().distinct().limit(QUANTIDADE_EXERCICIOS).collect(Collectors.toList());
        if (exercicios.isEmpty()) {
            throw new BadRequestException("Você ainda não tem erros para praticar.");
        }
        return montarSessaoPratica(usuario, exercicios, SessionType.ERRORS, null, "Prática: Erros");
    }

    /** Root: pratica os exercícios que o usuário errou em um assunto (tag) específico. */
    @Transactional
    public SessaoDto.InicioResponse iniciarPraticaErrosPorAssunto(String assunto, String username) {
        Usuario usuario = buscarUsuario(username);
        if (!vidasService.isRootAtivo(usuario)) {
            throw new BadRequestException("Revisar erros por assunto é exclusivo para assinantes Root.");
        }
        if (assunto == null || assunto.isBlank()) {
            throw new BadRequestException("Assunto inválido.");
        }
        String alvo = assunto.trim();
        List<Exercise> exercicios = practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario)
                .stream()
                .distinct()
                .filter(ex -> parseTags(ex.getTags()).stream().anyMatch(tag -> tag.equalsIgnoreCase(alvo)))
                .limit(QUANTIDADE_EXERCICIOS)
                .collect(Collectors.toList());
        if (exercicios.isEmpty()) {
            throw new BadRequestException("Você não tem erros nesse assunto para revisar.");
        }
        return montarSessaoPratica(usuario, exercicios, SessionType.ERRORS, null, "Revisar: " + alvo);
    }

    private SessaoDto.InicioResponse iniciarPraticaFixacao(Usuario usuario) {
        List<Exercise> selecionados = selecionarExerciciosDeModulosConcluidos(usuario, QUANTIDADE_EXERCICIOS_FIXACAO);
        return montarSessaoPratica(usuario, selecionados, SessionType.QUICK_FIX, null, "Prática: Fixação");
    }

    private SessaoDto.InicioResponse iniciarPraticaCronometrada(Usuario usuario) {
        List<Exercise> selecionados = selecionarExerciciosDeModulosConcluidos(usuario, QUANTIDADE_EXERCICIOS_FIXACAO);
        return montarSessaoPratica(usuario, selecionados, SessionType.TIMED, null, "Prática: Cronometrado");
    }

    private List<Exercise> selecionarExerciciosDeModulosConcluidos(Usuario usuario, int limite) {
        List<Modulo> modulosConcluidos = userProgressRepository
                .findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED)
                .stream()
                .map(UserProgress::getModulo)
                .toList();
        if (modulosConcluidos.isEmpty()) {
            throw new BadRequestException("Conclua um módulo antes de praticar.");
        }

        List<Exercise> pool = new ArrayList<>();
        for (Modulo modulo : modulosConcluidos) {
            pool.addAll(exerciseRepository.findByModuloOrderByIdAsc(modulo));
        }
        if (pool.isEmpty()) {
            throw new BadRequestException("Conclua um módulo antes de praticar.");
        }

        Collections.shuffle(pool);
        return pool.stream()
                .distinct()
                .limit(limite)
                .collect(Collectors.toList());
    }

    /** Plumbing compartilhado: transforma uma lista de exercícios numa sessão sem módulo. */
    private SessaoDto.InicioResponse montarSessaoPratica(Usuario usuario, List<Exercise> exercicios,
            SessionType tipo, Integer timeLimitSeconds, String titulo) {
        List<Exercise> ordenados = embaralharSemTiposConsecutivos(new ArrayList<>(exercicios));

        UserStats stats = userStatsRepository.findByUsuario(usuario)
                .orElseGet(() -> UserStats.builder().usuario(usuario).totalXp(0)
                        .currentLives(MAX_VIDAS).currentStreak(0).highestStreak(0).build());
        vidasService.aplicarRecarga(stats);
        userStatsRepository.save(stats);

        PracticeSession sessao = PracticeSession.builder()
                .usuario(usuario)
                .modulo(null)
                .sessionType(tipo)
                .startedAt(LocalDateTime.now())
                .timeLimitSeconds(timeLimitSeconds)
                .build();
        practiceSessionRepository.save(sessao);

        List<PracticeSessionExercise> sessionExercises = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            sessionExercises.add(PracticeSessionExercise.builder()
                    .practiceSession(sessao)
                    .exercise(ordenados.get(i))
                    .displayOrder(i + 1)
                    .build());
        }
        practiceSessionExerciseRepository.saveAll(sessionExercises);

        List<SessaoDto.ExercicioSessao> dtos = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            dtos.add(toExercicioSessao(ordenados.get(i), i + 1, tipo));
        }

        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(titulo)
                .initialLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .totalExercises(dtos.size())
                .timeLimitSeconds(timeLimitSeconds)
                .exercises(dtos)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean moduloJaConcluido(Usuario usuario, PracticeSession sessao) {
        return sessao.getModulo() != null && userProgressRepository
                .findByUsuarioAndModulo(usuario, sessao.getModulo())
                .map(p -> p.getStatus() == ProgressStatus.COMPLETED)
                .orElse(false);
    }

    private Usuario buscarUsuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));
    }

    /**
     * Define a seleção/ordem dos exercícios da sessão. Com a IA (OpenAI) configurada,
     * ela organiza de forma adaptativa a partir do contexto do aluno; sem ela — ou se a
     * IA falhar — usa o algoritmo determinístico {@link #selecionarExercicios(Modulo)}.
     */
    private List<Exercise> organizarExercicios(Modulo modulo, Usuario usuario, UserStats stats) {
        List<Exercise> candidatos = exerciseRepository.findByModuloOrderByIdAsc(modulo);
        if (candidatos.isEmpty()) {
            return List.of();
        }
        List<Exercise> organizadosIa = openAiOrganizacaoService.organizar(
                candidatos, usuario, stats, QUANTIDADE_EXERCICIOS);
        if (!organizadosIa.isEmpty()) {
            log.info("Sessão organizada pela IA (OpenAI) — módulo={} usuário={}",
                    modulo.getId(), usuario.getUsername());
            return organizadosIa;
        }
        return selecionarExercicios(modulo);
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
    private SessaoDto.ExercicioSessao toExercicioSessao(Exercise exercise, int ordem, SessionType sessionType) {
        String dadosExibicao = prepararDadosExibicao(exercise);

        return SessaoDto.ExercicioSessao.builder()
                .id(exercise.getId())
                .order(ordem)
                .statement(exercise.getStatement())
                .tipo(exercise.getExerciseType().name())
                .displayData(dadosExibicao)
                .xpReward(exercise.getXpReward())
                .relatedTopics(parseTags(exercise.getTags()))
                .imageData(exercise.getImageData())
                .timeLimitSeconds(sessionType == SessionType.TIMED
                        ? tempoLimiteSegundosPorXp(exercise.getXpReward())
                        : null)
                .build();
    }

    /** 3 XP → 1 min, 5 XP → 1,5 min, 7 XP → 2 min. */
    private static int tempoLimiteSegundosPorXp(int xpReward) {
        return switch (xpReward) {
            case 5 -> 90;
            case 7 -> 120;
            default -> 60;
        };
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
                Collections.shuffle(displayOptions); // posição da correta varia a cada sessão
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
