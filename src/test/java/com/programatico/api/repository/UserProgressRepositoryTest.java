package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.ProgressStatus;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserProgressRepositoryTest {

    @Autowired private UserProgressRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;
    private Modulo modulo1;
    private Modulo modulo2;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("prog-user")
                .email("prog@test.com")
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
        modulo1 = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("M1")
                .moduleType(ModuleType.STUDY)
                .displayOrder(1)
                .build());
        modulo2 = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("M2")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(2)
                .build());

        repository.save(progress(modulo1, ProgressStatus.COMPLETED));
        repository.save(progress(modulo2, ProgressStatus.UNLOCKED));
    }

    @Test
    void findByUsuarioAndModulo() {
        assertTrue(repository.findByUsuarioAndModulo(usuario, modulo1).isPresent());
        assertEquals(ProgressStatus.COMPLETED, repository.findByUsuarioAndModulo(usuario, modulo1).get().getStatus());
    }

    @Test
    void findByUsuarioAndStatus() {
        List<UserProgress> concluidos = repository.findByUsuarioAndStatus(usuario, ProgressStatus.COMPLETED);
        assertEquals(1, concluidos.size());
        assertEquals(modulo1.getId(), concluidos.get(0).getModulo().getId());
    }

    @Test
    void findByUsuarioAndModuloIn() {
        List<UserProgress> lista = repository.findByUsuarioAndModuloIn(usuario, List.of(modulo1, modulo2));
        assertEquals(2, lista.size());
    }

    @Test
    void deleteByUsuarioId() {
        repository.deleteByUsuarioId(usuario.getId());
        entityManager.flush();
        entityManager.clear();
        assertTrue(repository.findAll().isEmpty());
    }

    private UserProgress progress(Modulo modulo, ProgressStatus status) {
        return UserProgress.builder()
                .usuario(usuario)
                .modulo(modulo)
                .status(status)
                .build();
    }
}
