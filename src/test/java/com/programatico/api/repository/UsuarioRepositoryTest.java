package com.programatico.api.repository;

import com.programatico.api.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

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
}
