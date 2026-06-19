package com.programatico.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.ReviewDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UserDailyMissionRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");
    private static final List<Integer> ALLOWED_DAYS = List.of(7, 15, 30, 90);

    private final UsuarioRepository usuarioRepository;
    private final TrackRepository trackRepository;
    private final UserStatsRepository userStatsRepository;
    private final MissionRepository missionRepository;
    private final UserDailyMissionRepository userDailyMissionRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ReviewDto.Response getReview(String username, Long trackId, Integer days) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado para o token informado."));

        int selectedDays = normalizeDays(days);
        List<Track> availableTracks = trackRepository.findAllByOrderByDisplayOrderAsc();
        Track selectedTrack = resolveSelectedTrack(availableTracks, trackId);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(selectedDays - 1L);
        LocalDateTime startedAt = startDate.atStartOfDay();

        List<PracticeSession> sessions = selectedTrack == null
                ? practiceSessionRepository.findByUsuarioAndStartedAtGreaterThanEqualOrderByStartedAtAsc(usuario, startedAt)
                : practiceSessionRepository.findByUsuarioAndModulo_TrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                        usuario, selectedTrack, startedAt);

        List<PracticeSessionExercise> answers = sessions.isEmpty()
                ? List.of()
                : practiceSessionExerciseRepository.findByPracticeSessionIn(sessions);

        Map<LocalDate, DailyCounters> dailyCounters = buildDailyCounters(startDate, selectedDays);
        Map<String, SubjectCounters> subjectCounters = new LinkedHashMap<>();
        Map<Long, List<PracticeSessionExercise>> answersBySessionId = answers.stream()
                .filter(answer -> answer.getPracticeSession() != null && answer.getPracticeSession().getId() != null)
                .collect(Collectors.groupingBy(answer -> answer.getPracticeSession().getId()));

        int correctAnswers = 0;
        int incorrectAnswers = 0;
        long totalSeconds = 0L;
        int timedAnswers = 0;

        for (PracticeSession session : sessions) {
            if (session.getStartedAt() == null || session.getId() == null) {
                continue;
            }

            DailyCounters counters = dailyCounters.get(session.getStartedAt().toLocalDate());
            if (counters == null) {
                continue;
            }

            List<PracticeSessionExercise> sessionAnswers = answersBySessionId.getOrDefault(session.getId(), List.of());
            long sessionSeconds = getSessionDurationSeconds(session);

            for (PracticeSessionExercise answer : sessionAnswers) {
                boolean isCorrect = Boolean.TRUE.equals(answer.getIsCorrect());
                if (isCorrect) {
                    counters.acertos++;
                    correctAnswers++;
                } else {
                    counters.erros++;
                    incorrectAnswers++;
                }

                registerSubjects(subjectCounters, answer, isCorrect);
            }

            if (!sessionAnswers.isEmpty() && sessionSeconds > 0) {
                totalSeconds += sessionSeconds;
                timedAnswers += sessionAnswers.size();
            }
        }

        int totalAnswers = correctAnswers + incorrectAnswers;
        int accuracyPercent = totalAnswers == 0 ? 0 : Math.round((correctAnswers * 100f) / totalAnswers);
        int averageSeconds = timedAnswers == 0 ? 0 : Math.round(totalSeconds / (float) timedAnswers);

        List<Mission> missions = missionRepository.findAll().stream()
                .sorted(Comparator.comparing(Mission::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        Map<Long, UserDailyMission> dailyMissionMap = userDailyMissionRepository
                .findByUsuarioAndMissionDate(usuario, today)
                .stream()
                .filter(daily -> daily.getMission() != null && daily.getMission().getId() != null)
                .collect(Collectors.toMap(daily -> daily.getMission().getId(), daily -> daily, (a, b) -> a));

        int completedMissions = (int) dailyMissionMap.values().stream()
                .filter(daily -> Boolean.TRUE.equals(daily.getCompleted()))
                .count();

        int currentXp = userStatsRepository.findByUsuario(usuario)
                .map(UserStats::getTotalXp)
                .orElse(0);

        return ReviewDto.Response.builder()
                .selectedTrackId(selectedTrack != null ? selectedTrack.getId() : null)
                .selectedDays(selectedDays)
                .currentXp(currentXp)
                .availableTracks(toTrackOptions(availableTracks))
                .stats(buildStats(
                        totalAnswers,
                        accuracyPercent,
                        correctAnswers,
                        incorrectAnswers,
                        completedMissions,
                        missions.size(),
                        averageSeconds))
                .performanceData(toPerformanceData(dailyCounters))
                .subjectAccuracy(toSubjectAccuracy(subjectCounters))
                .errorsBySubject(toErrorsBySubject(subjectCounters))
                .reviewNow(toReviewNow(subjectCounters))
                .recentMissions(toRecentMissions(missions, dailyMissionMap))
                .build();
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return 7;
        }
        return ALLOWED_DAYS.contains(days) ? days : 7;
    }

    private Track resolveSelectedTrack(List<Track> availableTracks, Long trackId) {
        if (trackId == null) {
            return availableTracks.isEmpty() ? null : availableTracks.get(0);
        }

        return availableTracks.stream()
                .filter(track -> trackId.equals(track.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Trilha informada nao encontrada."));
    }

    private Map<LocalDate, DailyCounters> buildDailyCounters(LocalDate startDate, int selectedDays) {
        Map<LocalDate, DailyCounters> counters = new LinkedHashMap<>();
        for (int i = 0; i < selectedDays; i++) {
            LocalDate day = startDate.plusDays(i);
            counters.put(day, new DailyCounters(day));
        }
        return counters;
    }

    private long getSessionDurationSeconds(PracticeSession session) {
        if (session.getStartedAt() == null || session.getEndedAt() == null || session.getEndedAt().isBefore(session.getStartedAt())) {
            return 0L;
        }
        return Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
    }

    private void registerSubjects(Map<String, SubjectCounters> subjectCounters, PracticeSessionExercise answer, boolean isCorrect) {
        if (answer.getExercise() == null) {
            return;
        }

        for (String tag : parseTags(answer.getExercise().getTags())) {
            SubjectCounters counters = subjectCounters.computeIfAbsent(tag, ignored -> new SubjectCounters(tag));
            counters.total++;
            if (isCorrect) {
                counters.correct++;
            } else {
                counters.incorrect++;
            }
        }
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(tags, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .toList();
        }
    }

    private List<ReviewDto.TrackOption> toTrackOptions(List<Track> availableTracks) {
        return availableTracks.stream()
                .map(track -> ReviewDto.TrackOption.builder()
                        .id(track.getId())
                        .title(track.getTitle())
                        .build())
                .toList();
    }

    private List<ReviewDto.StatCard> buildStats(
            int totalAnswers,
            int accuracyPercent,
            int correctAnswers,
            int incorrectAnswers,
            int completedMissions,
            int totalMissions,
            int averageSeconds) {
        List<ReviewDto.StatCard> stats = new ArrayList<>();
        stats.add(ReviewDto.StatCard.builder()
                .title("Exercicios feitos")
                .value(String.valueOf(totalAnswers))
                .subtitle("No periodo selecionado")
                .build());
        stats.add(ReviewDto.StatCard.builder()
                .title("Taxa de acertos")
                .value(accuracyPercent + "%")
                .subtitle(correctAnswers + " acertos / " + incorrectAnswers + " erros")
                .build());
        stats.add(ReviewDto.StatCard.builder()
                .title("Missoes concluidas")
                .value(String.valueOf(completedMissions))
                .subtitle("de " + totalMissions + " disponiveis")
                .build());
        stats.add(ReviewDto.StatCard.builder()
                .title("Tempo medio")
                .value(averageSeconds + "s")
                .subtitle("por exercicio")
                .build());
        return stats;
    }

    private List<ReviewDto.PerformancePoint> toPerformanceData(Map<LocalDate, DailyCounters> dailyCounters) {
        return dailyCounters.values().stream()
                .map(counter -> ReviewDto.PerformancePoint.builder()
                        .day(formatDayLabel(counter.day))
                        .acertos(counter.acertos)
                        .erros(counter.erros)
                        .build())
                .toList();
    }

    private String formatDayLabel(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        String label = dayOfWeek.getDisplayName(TextStyle.SHORT, LOCALE_PT_BR).replace(".", "");
        return label.isEmpty() ? "" : label.substring(0, 1).toUpperCase(LOCALE_PT_BR) + label.substring(1);
    }

    private List<ReviewDto.SubjectAccuracyItem> toSubjectAccuracy(Map<String, SubjectCounters> subjectCounters) {
        return subjectCounters.values().stream()
                .sorted(Comparator.comparingInt(SubjectCounters::accuracyPercent).reversed()
                        .thenComparing(subject -> subject.subject))
                .map(subject -> ReviewDto.SubjectAccuracyItem.builder()
                        .assunto(subject.subject)
                        .percentual(subject.accuracyPercent())
                        .color(resolveSubjectColor(subject.accuracyPercent()))
                        .build())
                .toList();
    }

    private List<ReviewDto.ErrorBySubjectItem> toErrorsBySubject(Map<String, SubjectCounters> subjectCounters) {
        return subjectCounters.values().stream()
                .filter(subject -> subject.incorrect > 0)
                .sorted(Comparator.comparingInt((SubjectCounters subject) -> subject.incorrect).reversed()
                        .thenComparing(subject -> subject.subject))
                .map(subject -> ReviewDto.ErrorBySubjectItem.builder()
                        .assunto(subject.subject)
                        .erros(subject.incorrect)
                        .build())
                .toList();
    }

    private List<ReviewDto.ReviewNowItem> toReviewNow(Map<String, SubjectCounters> subjectCounters) {
        return subjectCounters.values().stream()
                .sorted(Comparator.comparingInt(SubjectCounters::accuracyPercent)
                        .thenComparing(Comparator.comparingInt((SubjectCounters subject) -> subject.incorrect).reversed())
                        .thenComparing(subject -> subject.subject))
                .limit(4)
                .map(subject -> ReviewDto.ReviewNowItem.builder()
                        .assunto(subject.subject)
                        .build())
                .toList();
    }

    private List<ReviewDto.RecentMissionItem> toRecentMissions(List<Mission> missions, Map<Long, UserDailyMission> dailyMissionMap) {
        return missions.stream()
                .limit(4)
                .map(mission -> {
                    UserDailyMission daily = dailyMissionMap.get(mission.getId());
                    return ReviewDto.RecentMissionItem.builder()
                            .label(mission.getTitle())
                            .status(resolveMissionStatus(mission, daily))
                            .build();
                })
                .toList();
    }

    private String resolveMissionStatus(Mission mission, UserDailyMission daily) {
        if (daily == null) {
            return "Pendente";
        }
        if (Boolean.TRUE.equals(daily.getCompleted())) {
            return "Concluida";
        }

        int progress = Optional.ofNullable(daily.getCurrentProgress()).orElse(0);
        int goal = Optional.ofNullable(daily.getGoal())
                .orElse(Optional.ofNullable(mission.getQuantidade()).orElse(1));
        if (progress > 0) {
            return "Em progresso (" + progress + "/" + goal + ")";
        }
        return "Pendente";
    }

    private String resolveSubjectColor(int accuracyPercent) {
        if (accuracyPercent >= 80) {
            return "#578f48"; // --color-success
        }
        if (accuracyPercent >= 60) {
            return "#d4a843"; // --color-premium
        }
        return "#ff6b6b"; // --color-error-heart
    }

    private static final class DailyCounters {
        private final LocalDate day;
        private int acertos;
        private int erros;

        private DailyCounters(LocalDate day) {
            this.day = day;
        }
    }

    private static final class SubjectCounters {
        private final String subject;
        private int total;
        private int correct;
        private int incorrect;

        private SubjectCounters(String subject) {
            this.subject = subject;
        }

        private int accuracyPercent() {
            return total == 0 ? 0 : Math.round((correct * 100f) / total);
        }
    }
}
