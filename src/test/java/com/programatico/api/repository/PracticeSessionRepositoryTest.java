package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.PracticeSession;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.SessionType;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.persistence.EntityManager;
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
class PracticeSessionRepositoryTest {

    @Autowired private PracticeSessionRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private Usuario outroUsuario;
    private Track track;
    private Modulo modulo;
    private PracticeSession sessaoUsuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("sess-user")
                .email("sess@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
        outroUsuario = usuarioRepository.save(Usuario.builder()
                .username("outro-user")
                .email("outro@test.com")
                .senha("hash")
                .idade(22)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        track = trackRepository.save(Track.builder()
                .title("Trilha")
                .description("Desc")
                .displayOrder(1)
                .build());
        modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(1)
                .build());

        LocalDateTime agora = LocalDateTime.now();
        sessaoUsuario = repository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(agora.minusHours(2))
                .endedAt(agora.minusHours(1))
                .build());
        repository.save(PracticeSession.builder()
                .usuario(usuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(agora.minusMinutes(30))
                .build());
        repository.save(PracticeSession.builder()
                .usuario(outroUsuario)
                .modulo(modulo)
                .sessionType(SessionType.ACTIVITY)
                .startedAt(agora.minusHours(1))
                .endedAt(agora)
                .build());
    }

    @Test
    void findByIdAndUsuario() {
        assertTrue(repository.findByIdAndUsuario(sessaoUsuario.getId(), usuario).isPresent());
        assertFalse(repository.findByIdAndUsuario(sessaoUsuario.getId(), outroUsuario).isPresent());
    }

    @Test
    void countByEndedAtIsNullAndStartedAtAfter() {
        long abertas = repository.countByEndedAtIsNullAndStartedAtAfter(LocalDateTime.now().minusHours(1));
        assertEquals(1L, abertas);
    }

    @Test
    void findByUsuarioAndStartedAtGreaterThanEqualOrderByStartedAtAsc() {
        List<PracticeSession> sessoes = repository.findByUsuarioAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                usuario, LocalDateTime.now().minusHours(3));

        assertEquals(2, sessoes.size());
        assertTrue(sessoes.get(0).getStartedAt().isBefore(sessoes.get(1).getStartedAt())
                || sessoes.get(0).getStartedAt().isEqual(sessoes.get(1).getStartedAt()));
    }

    @Test
    void findByUsuarioAndModuloTrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc() {
        List<PracticeSession> sessoes = repository
                .findByUsuarioAndModulo_TrackAndStartedAtGreaterThanEqualOrderByStartedAtAsc(
                        usuario, track, LocalDateTime.now().minusHours(3));

        assertEquals(2, sessoes.size());
    }

    @Test
    void deleteByUsuarioId() {
        repository.deleteByUsuarioId(usuario.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, repository.count());
        assertTrue(repository.findAll().stream().allMatch(s -> s.getUsuario().getId().equals(outroUsuario.getId())));
    }
}
