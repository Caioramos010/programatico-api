package com.programatico.api.repository;

import com.programatico.api.domain.TwoFactorBackupCode;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TwoFactorBackupCodeRepositoryTest {

    @Autowired private TwoFactorBackupCodeRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("2fa-user")
                .email("2fa@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
    }

    @Test
    void findByUsuarioIdAndUsedAtIsNullECount() {
        repository.save(code("hash-1", null));
        repository.save(code("hash-2", Instant.now()));
        repository.save(code("hash-3", null));

        List<TwoFactorBackupCode> disponiveis = repository.findByUsuarioIdAndUsedAtIsNull(usuario.getId());

        assertEquals(2, disponiveis.size());
        assertEquals(2L, repository.countByUsuarioIdAndUsedAtIsNull(usuario.getId()));
    }

    @Test
    void deleteByUsuarioId() {
        repository.save(code("hash-a", null));
        repository.save(code("hash-b", null));

        repository.deleteByUsuarioId(usuario.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findAll().isEmpty());
    }

    private TwoFactorBackupCode code(String hash, Instant usedAt) {
        return TwoFactorBackupCode.builder()
                .usuario(usuario)
                .codeHash(hash)
                .usedAt(usedAt)
                .createdAt(Instant.now())
                .build();
    }
}
