package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.AdminUsuarioDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AdminUsuarioService adminUsuarioService;

    @Test
    void listarTodosDeveRetornarTodosUsuariosAtivosQuandoBuscaNula() {
        Usuario ana = usuarioBase(1L, "ana", "ana@email.com");
        Usuario bia = usuarioBase(2L, "bia", "bia@email.com");
        when(usuarioRepository.findAll()).thenReturn(List.of(ana, bia));

        List<AdminUsuarioDto.Response> response = adminUsuarioService.listarTodos(null);

        assertEquals(2, response.size());
    }

    @Test
    void listarTodosDeveFiltrarUsuariosComSoftDelete() {
        Usuario ativo = usuarioBase(1L, "ativo", "ativo@email.com");
        Usuario deletado = usuarioBase(2L, "deletado", "deletado@email.com");
        deletado.setDeletedAt(Instant.now());
        when(usuarioRepository.findAll()).thenReturn(List.of(ativo, deletado));

        List<AdminUsuarioDto.Response> response = adminUsuarioService.listarTodos(null);

        assertEquals(1, response.size());
        assertEquals("ativo", response.get(0).getUsername());
    }

    @Test
    void listarTodosDeveFiltrarPorBuscaNoUsernameOuEmail() {
        Usuario carlos = usuarioBase(1L, "carlos", "carlos@email.com");
        Usuario diego = usuarioBase(2L, "diego", "diego@outro.com");
        when(usuarioRepository.findAll()).thenReturn(List.of(carlos, diego));

        List<AdminUsuarioDto.Response> response = adminUsuarioService.listarTodos("CARLOS");

        assertEquals(1, response.size());
        assertEquals("carlos", response.get(0).getUsername());
    }

    @Test
    void atualizarDeveAlterarRoleEAtivoDoUsuario() {
        Usuario usuario = usuarioBase(1L, "eva", "eva@email.com");
        AdminUsuarioDto.UpdateRequest request = AdminUsuarioDto.UpdateRequest.builder()
                .role(TipoUsuario.ADMIN)
                .ativo(false)
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        AdminUsuarioDto.Response response = adminUsuarioService.atualizar(1L, request);

        assertEquals(TipoUsuario.ADMIN, response.getRole());
        assertEquals(false, response.getAtivo());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminUsuarioService.atualizar(99L, AdminUsuarioDto.UpdateRequest.builder()
                        .role(TipoUsuario.USER).ativo(true).build()));
    }

    @Test
    void deletarDeveAplicarExclusaoLogicaPreenchendoDeletedAt() {
        Usuario usuario = usuarioBase(1L, "fred", "fred@email.com");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        adminUsuarioService.deletar(1L);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    void deletarDeveLancarExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminUsuarioService.deletar(99L));
        verify(usuarioRepository, never()).save(any());
    }

    private Usuario usuarioBase(Long id, String username, String email) {
        return Usuario.builder()
                .id(id)
                .username(username)
                .email(email)
                .senha("hash")
                .ativo(true)
                .role(TipoUsuario.USER)
                .dataCriacao(Instant.now())
                .build();
    }
}
