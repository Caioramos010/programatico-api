package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void iniciarLoginDeveEnviarCodigoQuandoCredenciaisForemValidas() {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.MessageResponse response = usuarioService.iniciarLogin(request);

        assertNotNull(response.getMensagem());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCodigoVerificacaoLogin());
        verify(emailService).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
    }

    @Test
    void confirmarLoginDeveRetornarTokenQuandoCodigoValido() {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("123456")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);
        usuario.setCodigoVerificacaoLogin("123456");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().plusSeconds(3600));

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.gerarToken("user", 1L)).thenReturn("jwt-token");

        UsuarioDto.LoginResponse response = usuarioService.confirmarLogin(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTipo());
        assertNotNull(response.getUsuario());
    }

    @Test
    void iniciarLoginDeveFalharQuandoContaNaoAtivada() {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(false);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));

        assertThrows(BadRequestException.class, () -> usuarioService.iniciarLogin(request));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void registroDeveFalharQuandoEmailJaExiste() {
        UsuarioDto.RegistroRequest request = UsuarioDto.RegistroRequest.builder()
                .username("novo-user")
                .email("novo@email.com")
                .senha("Senha@123")
                .idade(20)
                .build();

        when(usuarioRepository.existsByEmail("novo@email.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> usuarioService.registro(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void solicitarRedefinicaoSenhaDeveGerarCodigoEEnviarEmail() {
        Usuario usuario = usuarioBase();
        UsuarioDto.SolicitarRedefinicaoSenhaRequest request =
                UsuarioDto.SolicitarRedefinicaoSenhaRequest.builder()
                        .email("user@email.com")
                        .build();

        when(usuarioRepository.findByEmail("user@email.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.MessageResponse response = usuarioService.solicitarRedefinicaoSenha(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario salvo = captor.getValue();

        assertNotNull(salvo.getCodigoRedefinicaoSenha());
        assertFalse(salvo.getCodigoRedefinicaoSenha().isBlank());
        assertNotNull(salvo.getDataExpiracaoCodigoRedefinicao());
        assertTrue(salvo.getDataExpiracaoCodigoRedefinicao().isAfter(Instant.now()));
        verify(emailService).enviarCodigoRedefinicaoSenha("user@email.com", "user", salvo.getCodigoRedefinicaoSenha());
        assertNotNull(response);
    }

    @Test
    void redefinirSenhaDeveFalharQuandoCodigoExpirado() {
        Usuario usuario = usuarioBase();
        usuario.setCodigoRedefinicaoSenha("123456");
        usuario.setDataExpiracaoCodigoRedefinicao(Instant.now().minusSeconds(60));

        UsuarioDto.NovaSenhaRequest request = UsuarioDto.NovaSenhaRequest.builder()
                .codigo("123456")
                .novaSenha("NovaSenha@123")
                .build();

        when(usuarioRepository.findByCodigoRedefinicaoSenha("123456")).thenReturn(Optional.of(usuario));

        assertThrows(BadRequestException.class, () -> usuarioService.redefinirSenha(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void atualizarDeveFalharQuandoUsernameJaEmUsoPorOutroUsuario() {
        Usuario usuario = usuarioBase();
        UsuarioDto.UpdateRequest request = UsuarioDto.UpdateRequest.builder()
                .username("ja-existe")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByUsername("ja-existe")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> usuarioService.atualizar(1L, request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void confirmarExclusaoContaDeveFalharQuandoCodigoInvalido() {
        Usuario usuario = usuarioBase();
        usuario.setCodigoExclusaoConta("999999");
        usuario.setDataExpiracaoCodigoExclusao(Instant.now().plusSeconds(3600));

        UsuarioDto.ConfirmarExclusaoRequest request = UsuarioDto.ConfirmarExclusaoRequest.builder()
                .codigo("000000")
                .build();

        when(usuarioRepository.findById(anyLong())).thenReturn(Optional.of(usuario));

        assertThrows(BadRequestException.class, () -> usuarioService.confirmarExclusaoConta(1L, request));
        verify(usuarioRepository, never()).deleteById(anyLong());
    }

    @Test
    void excluirDeveFalharQuandoUsuarioNaoExiste() {
        when(usuarioRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.excluir(1L));
    }

    private Usuario usuarioBase() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setUsername("user");
        usuario.setEmail("user@email.com");
        usuario.setSenha("senha-hash");
        usuario.setAtivo(true);
        usuario.setDataCriacao(Instant.now());
        return usuario;
    }
}
