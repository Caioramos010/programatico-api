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
class UserMissionRepositoryTest {

    @Autowired private UserMissionRepository repository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("mission-user")
                .email("mission@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
    }

    @Test
    void findByUsuario() {
        Mission mission = missionRepository.save(Mission.builder()
                .title("Estudar")
                .objectiveType("XP")
                .xpReward(10)
                .quantidade(1)
                .build());

        repository.save(UserMission.builder()
                .id(usuario.getId() + "-" + mission.getId())
                .usuario(usuario)
                .mission(mission)
                .currentProgress(2)
                .isCompleted(false)
                .build());

        List<UserMission> missoes = repository.findByUsuario(usuario);
        assertEquals(1, missoes.size());
        assertEquals(2, missoes.get(0).getCurrentProgress());
    }

    @Test
    void deleteByUsuarioId() {
        Mission mission = missionRepository.save(Mission.builder()
                .title("Concluir")
                .objectiveType("XP")
                .xpReward(5)
                .quantidade(1)
                .build());

        repository.save(UserMission.builder()
                .id(usuario.getId() + "-" + mission.getId())
                .usuario(usuario)
                .mission(mission)
                .currentProgress(0)
                .isCompleted(false)
                .build());

        repository.deleteByUsuarioId(usuario.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findAll().isEmpty());
    }
}
