package com.programatico.api.repository;

import com.programatico.api.domain.Mission;
import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.Usuario;
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
class UserStatsRepositoryTest {

    @Autowired private UserStatsRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("stats-user")
                .email("stats@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
    }

    @Test
    void findByUsuario() {
        repository.save(com.programatico.api.domain.UserStats.builder()
                .usuario(usuario)
                .totalXp(100)
                .currentLives(5)
                .currentStreak(2)
                .highestStreak(3)
                .build());

        assertTrue(repository.findByUsuario(usuario).isPresent());
        assertEquals(100, repository.findByUsuario(usuario).get().getTotalXp());
    }

    @Test
    void deleteByUsuarioId() {
        repository.save(com.programatico.api.domain.UserStats.builder()
                .usuario(usuario)
                .totalXp(10)
                .currentLives(5)
                .build());

        repository.deleteByUsuarioId(usuario.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findAll().isEmpty());
    }
}
