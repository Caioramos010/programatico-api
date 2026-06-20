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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioDetailsService usuarioDetailsService;

    private Usuario usuario(TipoUsuario role, boolean ativo) {
        return Usuario.builder()
                .id(1L)
                .username("user")
                .senha("hash")
                .ativo(ativo)
                .role(role)
                .build();
    }

    @Test
    void carregaUsuarioAdminComAuthorityRoleAdmin() {
        when(usuarioRepository.findByEmailOrUsername("user", "user"))
                .thenReturn(Optional.of(usuario(TipoUsuario.ADMIN, true)));

        UserDetails details = usuarioDetailsService.loadUserByUsername("user");

        assertEquals("user", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void carregaUsuarioComumComAuthorityRoleUser() {
        when(usuarioRepository.findByEmailOrUsername("user", "user"))
                .thenReturn(Optional.of(usuario(TipoUsuario.USER, true)));

        UserDetails details = usuarioDetailsService.loadUserByUsername("user");

        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void usuarioInativoFicaDesabilitado() {
        when(usuarioRepository.findByEmailOrUsername("user", "user"))
                .thenReturn(Optional.of(usuario(TipoUsuario.USER, false)));

        UserDetails details = usuarioDetailsService.loadUserByUsername("user");

        assertFalse(details.isEnabled());
    }

    @Test
    void lancaQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByEmailOrUsername("ninguem", "ninguem"))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> usuarioDetailsService.loadUserByUsername("ninguem"));
    }
}
