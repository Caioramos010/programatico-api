package com.programatico.api.security;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private UsuarioDetailsService usuarioDetailsService;

    @Test
    void loadUserByUsernameDeveRetornarDetalhesComRole() {
        Usuario usuario = Usuario.builder()
                .username("john")
                .email("john@test.com")
                .senha("hash")
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build();

        when(usuarioRepository.findByEmailOrUsername("john", "john")).thenReturn(Optional.of(usuario));

        UserDetails details = usuarioDetailsService.loadUserByUsername("john");

        assertEquals("john", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsernameDeveLancarQuandoNaoEncontrado() {
        when(usuarioRepository.findByEmailOrUsername("missing", "missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> usuarioDetailsService.loadUserByUsername("missing"));
    }
}
