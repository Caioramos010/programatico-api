package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.AdminUsuarioDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private AdminUsuarioService adminUsuarioService;

    @Test
    void listarTodosDeveFiltrarPorBuscaEIgnorarDeletados() {
        Usuario ativo = usuario(1L, "alpha", "alpha@test.com", null);
        Usuario deletado = usuario(2L, "beta", "beta@test.com", Instant.now());
        when(usuarioRepository.findAll()).thenReturn(List.of(ativo, deletado));

        List<AdminUsuarioDto.Response> response = adminUsuarioService.listarTodos("alpha");

        assertEquals(1, response.size());
        assertEquals("alpha", response.get(0).getUsername());
    }

    @Test
    void atualizarDeveAlterarRoleEStatus() {
        Usuario usuario = usuario(1L, "user", "user@test.com", null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUsuarioDto.Response response = adminUsuarioService.atualizar(1L,
                AdminUsuarioDto.UpdateRequest.builder().role(TipoUsuario.ADMIN).ativo(false).build());

        assertEquals(TipoUsuario.ADMIN, response.getRole());
        assertEquals(false, response.getAtivo());
    }

    @Test
    void deletarDeveAplicarExclusaoLogica() {
        Usuario usuario = usuario(1L, "user", "user@test.com", null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        adminUsuarioService.deletar(1L);

        assertNotNull(usuario.getDeletedAt());
    }

    @Test
    void deletarDeveFalharQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> adminUsuarioService.deletar(99L));
    }

    private Usuario usuario(Long id, String username, String email, Instant deletedAt) {
        return Usuario.builder()
                .id(id)
                .username(username)
                .email(email)
                .role(TipoUsuario.USER)
                .ativo(true)
                .deletedAt(deletedAt)
                .build();
    }
}
