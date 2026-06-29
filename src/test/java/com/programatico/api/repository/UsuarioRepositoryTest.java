package com.programatico.api.repository;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void deveEncontrarPorEmailOuUsernameEValidarExists() {
        Usuario usuario = new Usuario();
        usuario.setUsername("repo-user");
        usuario.setEmail("repo-user@email.com");
        usuario.setSenha("hash");
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);

        Optional<Usuario> porEmail = usuarioRepository.findByEmailOrUsername("repo-user@email.com", "nao-usado");
        Optional<Usuario> porUsername = usuarioRepository.findByEmailOrUsername("nao-usado", "repo-user");

        assertTrue(porEmail.isPresent());
        assertTrue(porUsername.isPresent());
        assertTrue(usuarioRepository.existsByEmail("repo-user@email.com"));
        assertTrue(usuarioRepository.existsByUsername("repo-user"));
        assertFalse(usuarioRepository.existsByEmail("ausente@email.com"));
    }

    @Test
    void deveEncontrarPorCodigoAtivacaoERedefinicao() {
        Usuario usuario = new Usuario();
        usuario.setUsername("codigo-user");
        usuario.setEmail("codigo-user@email.com");
        usuario.setSenha("hash");
        usuario.setAtivo(false);
        usuario.setCodigoAtivacao("123456");
        usuario.setCodigoRedefinicaoSenha("654321");
        usuarioRepository.save(usuario);

        assertTrue(usuarioRepository.findByCodigoAtivacao("123456").isPresent());
        assertTrue(usuarioRepository.findByCodigoRedefinicaoSenha("654321").isPresent());
        assertFalse(usuarioRepository.findByCodigoAtivacao("000000").isPresent());
    }

    @Test
    void deveListarRootExpiradosExcluindoAtivosDeletadosESemData() {
        Instant passado = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant futuro = Instant.now().plus(10, ChronoUnit.DAYS);

        Usuario expirado = usuarioComAssinatura("root-expirado", SubscriptionType.ROOT, passado);
        Usuario rootAtivo = usuarioComAssinatura("root-ativo", SubscriptionType.ROOT, futuro);
        Usuario rootSemExpiracao = usuarioComAssinatura("root-vitalicio", SubscriptionType.ROOT, null);
        Usuario freeExpirado = usuarioComAssinatura("free-user", SubscriptionType.FREE, passado);
        Usuario deletadoExpirado = usuarioComAssinatura("root-deletado", SubscriptionType.ROOT, passado);
        deletadoExpirado.setDeletedAt(Instant.now());

        usuarioRepository.saveAll(List.of(expirado, rootAtivo, rootSemExpiracao, freeExpirado, deletadoExpirado));

        List<Usuario> result = usuarioRepository
                .findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                        SubscriptionType.ROOT, Instant.now());

        assertEquals(1, result.size());
        assertEquals("root-expirado", result.get(0).getUsername());
    }

    private static Usuario usuarioComAssinatura(String username, SubscriptionType type, Instant expiresAt) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setEmail(username + "@email.com");
        usuario.setSenha("hash");
        usuario.setAtivo(true);
        usuario.setSubscriptionType(type);
        usuario.setSubscriptionExpiresAt(expiresAt);
        return usuario;
    }
}
