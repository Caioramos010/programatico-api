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
import com.programatico.api.domain.enums.NivelHabilidade;
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
import com.programatico.api.util.Tags;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
import java.util.Random;
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
    /** Teto por intervalo entre interações no cômputo do tempo ativo da sessão. */
    private static final int CAP_INTERVALO_SEGUNDOS = 300;

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
                // Retomada: reancora o relógio do tempo ativo — o período em que o
                // usuário ficou fora não deve contar na duração da sessão.
                aberta.get().setLastInteractionAt(LocalDateTime.now());
                practiceSessionRepository.save(aberta.get());
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
                .map(ex -> toExercicioSessao(ex, selecionados.indexOf(ex) + 1, sessao))
                .collect(Collectors.toList());

        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(modulo.getTitle())
                .initialLives(vidasVisiveis(usuario, stats))
                .totalExercises(exerciciosDtos.size())
                .masteredIds(List.of())
                .exercises(exerciciosDtos)
                .build();
    }

    /** Retoma uma sessão aberta: devolve TODOS os alvos (mantém o total) + ids já dominados (fora da fila). */
    private SessaoDto.InicioResponse montarResume(PracticeSession sessao,
            List<PracticeSessionExercise> itens, UserStats stats) {
        List<SessaoDto.ExercicioSessao> dtos = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            dtos.add(toExercicioSessao(itens.get(i).getExercise(), i + 1, sessao));
        }
        List<Long> masteredIds = itens.stream()
                .filter(i -> Boolean.TRUE.equals(i.getMastered()))
                .map(i -> i.getExercise().getId())
                .collect(Collectors.toList());
        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(sessao.getModulo() != null ? sessao.getModulo().getTitle() : null)
                .initialLives(vidasVisiveis(sessao.getUsuario(), stats))
                .totalExercises(dtos.size())
                .resumedFrom(0)
                .masteredIds(masteredIds)
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

        registrarTempoAtivo(sessao);

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
            // XP só em sessão de MÓDULO, na 1ª tentativa do alvo e fora de replay.
            // Práticas (fixação/erros/cronometrado, modulo=null) não dão XP: cada
            // sessão nova recriava os alvos e virava farm infinito de XP.
            if (primeiraTentativa && sessao.getModulo() != null && !moduloJaConcluido(usuario, sessao)) {
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
        aplicarStreakDiario(stats);
        userStatsRepository.save(stats);

        return SessaoDto.RespostaResponse.builder()
                .correct(correto)
                .correctAnswer(respostaCorreta)
                .remainingLives(vidasVisiveis(usuario, stats))
                .relatedTopics(parseTags(exercise.getTags()))
                .build();
    }

    /**
     * Atualiza o daystreak na primeira atividade do dia: +1 se a última atividade
     * foi no dia anterior, reinicia em 1 se houve um dia sem atividade (ou é a
     * primeira), e mantém se já contou hoje. Atualiza também o recorde.
     */
    private void aplicarStreakDiario(UserStats stats) {
        LocalDate hoje = LocalDate.now();
        LocalDate ultima = stats.getLastActivityDate() != null
                ? stats.getLastActivityDate().toLocalDate()
                : null;
        int atual = stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;
        if (ultima == null || ultima.isBefore(hoje.minusDays(1))) {
            atual = 1;
        } else if (ultima.equals(hoje.minusDays(1))) {
            atual = atual + 1;
        } else {
            atual = Math.max(atual, 1);
        }
        stats.setCurrentStreak(atual);
        int recorde = stats.getHighestStreak() != null ? stats.getHighestStreak() : 0;
        stats.setHighestStreak(Math.max(recorde, atual));
        stats.setLastActivityDate(LocalDateTime.now());
    }

    @Transactional
    public SessaoDto.ConclusaoResponse concluir(Long sessaoId, String username) {
        Usuario usuario = buscarUsuario(username);
        PracticeSession sessao = practiceSessionRepository.findByIdAndUsuario(sessaoId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão", sessaoId));

        if (sessao.getEndedAt() != null) {
            throw new BadRequestException("Esta sessão já foi encerrada.");
        }

        // Fecha o cômputo do tempo ativo antes de encerrar (última interação → agora).
        registrarTempoAtivo(sessao);
        sessao.setEndedAt(LocalDateTime.now());
        practiceSessionRepository.save(sessao);

        List<PracticeSessionExercise> todos = practiceSessionExerciseRepository
                .findByPracticeSessionOrderByDisplayOrderAsc(sessao);

        long corretos = todos.stream().filter(e -> Boolean.TRUE.equals(e.getIsCorrect())).count();
        int taxaAcerto = todos.isEmpty() ? 0 : (int) (corretos * 100L / todos.size());
        // Replay de módulo concluído e PRÁTICAS (modulo=null) não pontuaram em
        // responder(); o relatório reflete o XP realmente creditado.
        boolean replay = moduloJaConcluido(usuario, sessao);
        boolean sessaoPontua = sessao.getModulo() != null && !replay;
        int xpGanho = !sessaoPontua ? 0 : todos.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsCorrect()))
                .mapToInt(e -> e.getExercise().getXpReward())
                .sum();
        // Tempo efetivo de prática (ausências não contam) — não o intervalo bruto
        // startedAt→endedAt, que inflava a duração em sessões retomadas dias depois.
        int duracao = sessao.getActiveSeconds() != null
                ? sessao.getActiveSeconds()
                : (int) Math.min(
                        ChronoUnit.SECONDS.between(sessao.getStartedAt(), sessao.getEndedAt()),
                        CAP_INTERVALO_SEGUNDOS);

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

        // Review por assunto (Root): acertos/erros por tag, com base na 1ª tentativa.
        Map<String, int[]> porAssunto = new LinkedHashMap<>();
        for (PracticeSessionExercise pse : todos) {
            if (pse.getIsCorrect() == null) continue;
            boolean acertou = Boolean.TRUE.equals(pse.getIsCorrect());
            for (String tag : parseTags(pse.getExercise().getTags())) {
                int[] c = porAssunto.computeIfAbsent(tag, k -> new int[2]);
                if (acertou) c[0]++; else c[1]++;
            }
        }
        List<SessaoDto.SubjectReview> subjectReview = porAssunto.entrySet().stream()
                .map(e -> SessaoDto.SubjectReview.builder()
                        .assunto(e.getKey()).acertos(e.getValue()[0]).erros(e.getValue()[1]).build())
                .collect(Collectors.toList());

        return SessaoDto.ConclusaoResponse.builder()
                .xpEarned(xpGanho)
                .accuracy(taxaAcerto)
                .durationSeconds(duracao)
                .remainingLives(stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS)
                .moduleCompleted(moduloConcluido)
                .firstCompletion(primeiraConclusao)
                .completedMissions(missoesConcluidas)
                .subjectReview(subjectReview)
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
        return toExercicioSessao(candidatos.get(0), 0, sessao);
    }

    // ── Práticas (esqueleto para Hyorran) ───────────────────────────────────────
    // O fluxo responder()/concluir() já é agnóstico de módulo (todos os acessos a
    // sessao.getModulo() têm guard de null), então cada modo de prática só precisa de
    // um "iniciar" próprio que produza um InicioResponse.

    @Transactional
    public SessaoDto.InicioResponse iniciarPratica(String modo, String username) {
        Usuario usuario = buscarUsuario(username);
        return switch (modo == null ? "" : modo.toLowerCase()) {
            case "erros" -> {
                exigirRoot(usuario, "A prática de erros é exclusiva para assinantes Root.");
                yield iniciarPraticaErros(usuario);
            }
            case "fixacao" -> {
                exigirRoot(usuario, "A prática de fixação é exclusiva para assinantes Root.");
                yield iniciarPraticaFixacao(usuario);
            }
            case "cronometrado" -> iniciarPraticaCronometrada(usuario);
            default -> throw new BadRequestException("Modo de prática inválido: " + modo);
        };
    }

    private void exigirRoot(Usuario usuario, String mensagem) {
        if (!vidasService.isRootAtivo(usuario)) {
            throw new BadRequestException(mensagem);
        }
    }

    /**
     * Acumula o tempo efetivo de prática: soma o intervalo desde a última
     * interação com teto de {@link #CAP_INTERVALO_SEGUNDOS} — pausas longas
     * (fechou o site e voltou depois) não inflam a duração da sessão.
     */
    private void registrarTempoAtivo(PracticeSession sessao) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ancora = sessao.getLastInteractionAt() != null
                ? sessao.getLastInteractionAt()
                : sessao.getStartedAt();
        long delta = ancora != null ? ChronoUnit.SECONDS.between(ancora, agora) : 0;
        int acumulado = sessao.getActiveSeconds() != null ? sessao.getActiveSeconds() : 0;
        sessao.setActiveSeconds(acumulado + (int) Math.max(0, Math.min(delta, CAP_INTERVALO_SEGUNDOS)));
        sessao.setLastInteractionAt(agora);
        practiceSessionRepository.save(sessao);
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

        // Revisar é praticar o TEMA, não decorar a questão: seleciona exercícios do
        // mesmo assunto priorizando (1) a dificuldade em que o usuário errou e
        // (2) questões diferentes das que ele errou; completa com o restante do
        // assunto (incluindo as erradas) quando o tema tem poucos exercícios.
        List<Exercise> errados = practiceSessionExerciseRepository.findExerciciosErradosDoUsuario(usuario)
                .stream()
                .distinct()
                .filter(ex -> temAssunto(ex, alvo))
                .toList();
        Set<Long> idsErrados = errados.stream().map(Exercise::getId).collect(Collectors.toSet());
        Set<Integer> dificuldadesErradas = errados.stream().map(Exercise::getXpReward).collect(Collectors.toSet());

        List<Exercise> doAssunto = exerciseRepository.findAll().stream()
                .filter(ex -> temAssunto(ex, alvo))
                .collect(Collectors.toList());
        if (doAssunto.isEmpty()) {
            throw new BadRequestException("Não há exercícios deste assunto para revisar.");
        }

        List<Exercise> prioritarios = shuffleList(doAssunto.stream()
                .filter(ex -> !idsErrados.contains(ex.getId()))
                .filter(ex -> dificuldadesErradas.isEmpty() || dificuldadesErradas.contains(ex.getXpReward()))
                .collect(Collectors.toList()));
        List<Exercise> selecionados = new ArrayList<>(
                prioritarios.stream().limit(QUANTIDADE_EXERCICIOS_FIXACAO).toList());
        if (selecionados.size() < QUANTIDADE_EXERCICIOS_FIXACAO) {
            List<Exercise> restantes = shuffleList(doAssunto.stream()
                    .filter(ex -> !selecionados.contains(ex))
                    .collect(Collectors.toList()));
            selecionados.addAll(restantes.stream()
                    .limit(QUANTIDADE_EXERCICIOS_FIXACAO - selecionados.size())
                    .toList());
        }
        return montarSessaoPratica(usuario, selecionados, SessionType.ERRORS, null, "Revisar: " + alvo);
    }

    private boolean temAssunto(Exercise exercise, String assunto) {
        return parseTags(exercise.getTags()).stream().anyMatch(tag -> tag.equalsIgnoreCase(assunto));
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
            dtos.add(toExercicioSessao(ordenados.get(i), i + 1, sessao));
        }

        return SessaoDto.InicioResponse.builder()
                .sessionId(sessao.getId())
                .moduleTitle(titulo)
                .initialLives(vidasVisiveis(usuario, stats))
                .totalExercises(dtos.size())
                .timeLimitSeconds(timeLimitSeconds)
                .masteredIds(List.of())
                .exercises(dtos)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Vidas exibidas ao usuário: Root ativo sempre vê o máximo — o front trata 0
     * como "sem vidas" e bloquearia um assinante que zerou as vidas antes de assinar.
     */
    private int vidasVisiveis(Usuario usuario, UserStats stats) {
        if (vidasService.temVidasIlimitadas(usuario)) {
            return MAX_VIDAS;
        }
        return stats.getCurrentLives() != null ? stats.getCurrentLives() : MAX_VIDAS;
    }

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
     * A rotação é determinística por sessão+exercício: retomar a sessão (ou pedir
     * reforço) mantém as posições; sessões diferentes rotacionam diferente.
     */
    private SessaoDto.ExercicioSessao toExercicioSessao(Exercise exercise, int ordem, PracticeSession sessao) {
        String dadosExibicao = prepararDadosExibicao(exercise, seedRotacao(sessao, exercise));

        return SessaoDto.ExercicioSessao.builder()
                .id(exercise.getId())
                .order(ordem)
                .statement(exercise.getStatement())
                .tipo(exercise.getExerciseType().name())
                .displayData(dadosExibicao)
                .xpReward(exercise.getXpReward())
                .relatedTopics(parseTags(exercise.getTags()))
                .imageData(exercise.getImageData())
                .timeLimitSeconds(sessao.getSessionType() == SessionType.TIMED
                        ? tempoLimiteSegundos(exercise.getXpReward(), sessao.getUsuario())
                        : null)
                .build();
    }

    private static long seedRotacao(PracticeSession sessao, Exercise exercise) {
        long sessaoId = sessao.getId() != null ? sessao.getId() : 0L;
        long exercicioId = exercise.getId() != null ? exercise.getId() : 0L;
        return sessaoId * 31L + exercicioId;
    }

    /**
     * Tempo do modo cronometrado: base por dificuldade (3 XP → 60s, 5 → 90s, 7 → 120s)
     * ajustada pelo nível do aluno — iniciante ×1,5, intermediário ×1,25, avançado ×1,0
     * (feedback da banca: considerar o nível de conhecimento do usuário).
     */
    private static int tempoLimiteSegundos(int xpReward, Usuario usuario) {
        int base = switch (xpReward) {
            case 5 -> 90;
            case 7 -> 120;
            default -> 60;
        };
        NivelHabilidade nivel = usuario != null ? usuario.getNivelHabilidade() : null;
        double fator = nivel == null ? 1.5 : switch (nivel) {
            case BEGINNER -> 1.5;
            case INTERMEDIATE -> 1.25;
            case ADVANCED -> 1.0;
        };
        return (int) Math.round(base * fator);
    }

    /**
     * Para DRAG_DROP, retorna os itens embaralhados (a ordem correta é o exerciseData original).
     * Para PAIRS, retorna os pares com a coluna direita embaralhada.
     * Para MULTIPLE_CHOICE, retorna as opções sem indicar qual é correta.
     * O embaralhamento usa o seed recebido (determinístico por sessão+exercício).
     * NUNCA devolve o exerciseData cru: ele contém o gabarito (flag correct,
     * ordem correta do drag-drop e pares corretos) — em erro de parse, falha explícito.
     */
    @SuppressWarnings("unchecked")
    private String prepararDadosExibicao(Exercise exercise, long seed) {
        Random random = new Random(seed);
        try {
            if (exercise.getExerciseType() == ExerciseType.DRAG_DROP) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<String> items = (List<String>) data.get("items");
                List<String> embaralhados = new ArrayList<>(items);
                Collections.shuffle(embaralhados, random);
                return objectMapper.writeValueAsString(Map.of("items", embaralhados));
            }
            if (exercise.getExerciseType() == ExerciseType.PAIRS) {
                Map<String, Object> data = objectMapper.readValue(exercise.getExerciseData(), new TypeReference<>() {});
                List<Map<String, String>> pairs = (List<Map<String, String>>) data.get("pairs");
                List<String> lefts = new ArrayList<>(pairs.stream().map(p -> p.get("left")).collect(Collectors.toList()));
                List<String> rights = new ArrayList<>(pairs.stream().map(p -> p.get("right")).collect(Collectors.toList()));
                Collections.shuffle(lefts, random);
                Collections.shuffle(rights, random);
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
                Collections.shuffle(displayOptions, random); // posição da correta varia por sessão
                return objectMapper.writeValueAsString(Map.of("options", displayOptions));
            }
            throw new IllegalStateException("Tipo de exercício não suportado: " + exercise.getExerciseType());
        } catch (Exception e) {
            log.error("Erro ao preparar dados de exibição para exercício id={}: {}", exercise.getId(), e.getMessage());
            throw new BadRequestException("Exercício com dados inválidos. Contate o suporte.");
        }
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
                // Normaliza os DOIS lados: o texto da opção pode conter aspas literais
                // (ex.: frases citadas em exercícios de lógica) e a resposta pode chegar
                // crua ou JSON-encoded — remover aspas só de um lado nunca casa.
                String respostaLimpa = respostaJson.replace("\"", "").trim();
                String corretaLimpa = descricaoCorreta.replace("\"", "").trim();
                return corretaLimpa.equalsIgnoreCase(respostaLimpa);
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
        return Tags.parse(tags);
    }
}
