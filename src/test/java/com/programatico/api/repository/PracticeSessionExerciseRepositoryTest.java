package com.programatico.api.repository;

import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.PracticeSessionExercise;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.TipoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PracticeSessionExerciseRepositoryTest {

    @Autowired private PracticeSessionExerciseRepository repository;
    @Autowired private PracticeSessionRepository sessionRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Exercise exerciseErrado;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("repo-user")
                .email("repo@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        Track track = trackRepository.save(Track.builder()
                .title("Trilha")
                .description("Desc")
                .displayOrder(1)
                .build());

        Modulo modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .description("Atividade")
                .build());

        exerciseErrado = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Errado")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(3)
                .build());

        Exercise exerciseCorreto = exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement("Correto")
                .exerciseType(ExerciseType.MULTIPLE_CHOICE)
                .exerciseData("{}")
                .xpReward(3)
                .build());

        PracticeSession sessao = sessionRepository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .build());

        repository.save(PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exerciseErrado)
                .displayOrder(1)
                .isCorrect(false)
                .build());
        repository.save(PracticeSessionExercise.builder()
                .practiceSession(sessao)
                .exercise(exerciseCorreto)
                .displayOrder(2)
                .isCorrect(true)
                .build());
    }

    @Test
    void findExerciciosErradosDoUsuarioDeveRetornarApenasErrados() {
        List<Exercise> errados = repository.findExerciciosErradosDoUsuario(usuario);

        assertEquals(1, errados.size());
        assertEquals(exerciseErrado.getId(), errados.get(0).getId());
    }

    @Test
    void findByPracticeSessionAndExerciseIdDeveEncontrarRegistro() {
        PracticeSession sessao = sessionRepository.findAll().get(0);

        var found = repository.findByPracticeSessionAndExerciseId(sessao, exerciseErrado.getId());

        assertFalse(found.isEmpty());
        assertEquals(false, found.get().getIsCorrect());
    }

    @Test
    void findByPracticeSessionIn() {
        PracticeSession sessao = sessionRepository.findAll().get(0);
        List<PracticeSessionExercise> itens = repository.findByPracticeSessionIn(List.of(sessao));
        assertEquals(2, itens.size());
    }

    @Test
    void findByExercise() {
        List<PracticeSessionExercise> itens = repository.findByExercise(exerciseErrado);
        assertEquals(1, itens.size());
        assertEquals(exerciseErrado.getId(), itens.get(0).getExercise().getId());
    }

    @Test
    void deleteByPracticeSessionUsuarioId() {
        repository.deleteByPracticeSessionUsuarioId(usuario.getId());
        assertTrue(repository.findAll().isEmpty());
    }
}
