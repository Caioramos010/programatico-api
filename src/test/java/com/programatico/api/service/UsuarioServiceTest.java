package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UserMissionRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private VerificationCodeGuardService verificationCodeGuardService;

    @Mock
    private TotpSettingsService totpSettingsService;

    @Mock
    private BackupCodeService backupCodeService;

    @Mock
    private TrustedDeviceService trustedDeviceService;

    @Mock
    private UserMissionRepository userMissionRepository;

    @Mock
    private UserProgressRepository userProgressRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeSessionExerciseRepository practiceSessionExerciseRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void confirmarLoginDeveFalharQuando2faDesativado() {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("123456")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> usuarioService.confirmarLogin(request));

        assertEquals("Verificação em duas etapas desativada para esta conta.", ex.getMessage());
        verify(verificationCodeGuardService, never()).ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
    }

    @Test
    void confirmarLoginDeveFalharQuandoCodigoLoginExpirado() {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("123456")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);
        usuario.setCodigoVerificacaoLogin("123456");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().minusSeconds(60));

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        when(totpSettingsService.isTotpAtivo(usuario)).thenReturn(false);
        when(backupCodeService.temCodigosDisponiveis(usuario)).thenReturn(false);
        doNothing().when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> usuarioService.confirmarLogin(request));

        assertEquals("Código expirado. Volte à tela de login e tente novamente.", ex.getMessage());
        verify(verificationCodeGuardService, never()).resetAttempts(usuario, VerificationCodeContext.LOGIN);
    }

    @Test
    void confirmarLoginDeveRegistrarTentativaQuandoCodigoInvalido() {
        UsuarioDto.LoginConfirmarRequest request = UsuarioDto.LoginConfirmarRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .codigo("000000")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);
        usuario.setCodigoVerificacaoLogin("123456");
        usuario.setDataExpiracaoCodigoLogin(Instant.now().plusSeconds(3600));

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        when(totpSettingsService.isTotpAtivo(usuario)).thenReturn(false);
        when(backupCodeService.temCodigosDisponiveis(usuario)).thenReturn(false);
        doNothing().when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
        doThrow(new BadRequestException("Código inválido. Restam 4 tentativa(s) antes do bloqueio temporário."))
                .when(verificationCodeGuardService)
                .recordFailedAttempt(usuario, VerificationCodeContext.LOGIN);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> usuarioService.confirmarLogin(request));

        assertTrue(ex.getMessage().contains("Código inválido"));
        verify(verificationCodeGuardService).recordFailedAttempt(usuario, VerificationCodeContext.LOGIN);
    }

    @Test
    void iniciarLoginDevePular2faQuandoDispositivoConfiavel() {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        when(trustedDeviceService.isConfiavel(1L, "device-token")).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.gerarToken("user", 1L)).thenReturn("jwt-token");

        UsuarioDto.LoginIniciarResponse response = usuarioService.iniciarLogin(request, "device-token");

        assertFalse(response.isRequiresVerification());
        assertEquals("jwt-token", response.getToken());
        verify(emailService, never()).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
        verify(verificationCodeGuardService, never()).ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
    }

    @Test
    void iniciarLoginDeveRespeitarBloqueioPorTentativas() {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        doThrow(new BadRequestException("Muitas tentativas inválidas. Tente novamente em 30 minutos."))
                .when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);

        assertThrows(BadRequestException.class, () -> usuarioService.iniciarLogin(request, null));
        verify(emailService, never()).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
    }

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
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        when(totpSettingsService.isTotpAtivo(usuario)).thenReturn(false);
        doNothing().when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.LoginIniciarResponse response = usuarioService.iniciarLogin(request, null);

        assertTrue(response.isRequiresVerification());
        assertNotNull(response.getMensagem());
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCodigoVerificacaoLogin());
        verify(emailService).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
    }

    @Test
    void iniciarLoginDeveRetornarTokenDiretoQuando2faDesativado() {
        UsuarioDto.LoginRequest request = UsuarioDto.LoginRequest.builder()
                .emailOuUsername("user")
                .senha("Senha@123")
                .build();
        Usuario usuario = usuarioBase();
        usuario.setAtivo(true);

        when(usuarioRepository.findByEmailOrUsername("user", "user")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha@123", "senha-hash")).thenReturn(true);
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.gerarToken("user", 1L)).thenReturn("jwt-token");

        UsuarioDto.LoginIniciarResponse response = usuarioService.iniciarLogin(request, null);

        assertFalse(response.isRequiresVerification());
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTipo());
        assertNotNull(response.getUsuario());
        verify(emailService, never()).enviarCodigoVerificacaoLogin(anyString(), anyString(), anyString());
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
        when(userSettingsService.isTwoFactorEnabled(usuario)).thenReturn(true);
        when(totpSettingsService.isTotpAtivo(usuario)).thenReturn(false);
        when(backupCodeService.temCodigosDisponiveis(usuario)).thenReturn(false);
        doNothing().when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
        doNothing().when(verificationCodeGuardService)
                .resetAttempts(usuario, VerificationCodeContext.LOGIN);
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

        assertThrows(BadRequestException.class, () -> usuarioService.iniciarLogin(request, null));
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

    @Test
    void registroDeveCriarUsuarioEEnviarEmailDeAtivacao() {
        UsuarioDto.RegistroRequest request = UsuarioDto.RegistroRequest.builder()
                .username("novo-user")
                .email("novo@email.com")
                .senha("Senha@123")
                .idade(20)
                .build();

        when(usuarioRepository.existsByEmail("novo@email.com")).thenReturn(false);
        when(usuarioRepository.existsByUsername("novo-user")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("hash-novo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario salvo = invocation.getArgument(0);
            salvo.setId(2L);
            return salvo;
        });

        UsuarioDto.Response response = usuarioService.registro(request);

        assertEquals("novo-user", response.getUsername());
        assertEquals("novo@email.com", response.getEmail());
        assertFalse(response.getAtivo());
        verify(emailService).enviarCodigoAtivacao(eq("novo@email.com"), eq("novo-user"), anyString());
    }

    @Test
    void ativarDeveAtivarContaQuandoCodigoValido() {
        Usuario usuario = usuarioBase();
        usuario.setAtivo(false);
        usuario.setCodigoAtivacao("123456");

        when(usuarioRepository.findByCodigoAtivacao("123456")).thenReturn(Optional.of(usuario));
        doNothing().when(verificationCodeGuardService)
                .resetAttempts(usuario, VerificationCodeContext.ACTIVATION);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.AtivacaoRequest request = UsuarioDto.AtivacaoRequest.builder()
                .codigo("123456")
                .build();

        UsuarioDto.MessageResponse response = usuarioService.ativar(request);

        assertEquals("Conta ativada com sucesso. Faça login.", response.getMensagem());
        verify(usuarioRepository).save(argThat(u -> Boolean.TRUE.equals(u.getAtivo()) && u.getCodigoAtivacao() == null));
    }

    @Test
    void solicitarAtivacaoDeveGerarNovoCodigoEEnviarEmail() {
        Usuario usuario = usuarioBase();
        usuario.setAtivo(false);

        when(usuarioRepository.findByEmail("user@email.com")).thenReturn(Optional.of(usuario));
        doNothing().when(verificationCodeGuardService)
                .ensureNotBlocked(usuario, VerificationCodeContext.ACTIVATION);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.SolicitarAtivacaoRequest request = UsuarioDto.SolicitarAtivacaoRequest.builder()
                .email("user@email.com")
                .build();

        UsuarioDto.MessageResponse response = usuarioService.solicitarAtivacao(request);

        assertTrue(response.getMensagem().contains("novo código"));
        verify(emailService).enviarCodigoAtivacao(eq("user@email.com"), eq("user"), anyString());
    }

    @Test
    void redefinirSenhaDeveAtualizarSenhaQuandoCodigoValido() {
        Usuario usuario = usuarioBase();
        usuario.setCodigoRedefinicaoSenha("123456");
        usuario.setDataExpiracaoCodigoRedefinicao(Instant.now().plusSeconds(3600));

        UsuarioDto.NovaSenhaRequest request = UsuarioDto.NovaSenhaRequest.builder()
                .codigo("123456")
                .novaSenha("NovaSenha@123")
                .build();

        when(usuarioRepository.findByCodigoRedefinicaoSenha("123456")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("NovaSenha@123")).thenReturn("nova-hash");
        doNothing().when(verificationCodeGuardService)
                .resetAttempts(usuario, VerificationCodeContext.PASSWORD_RESET);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.MessageResponse response = usuarioService.redefinirSenha(request);

        assertEquals("Senha alterada com sucesso. Faça login.", response.getMensagem());
        verify(usuarioRepository).save(argThat(u ->
                "nova-hash".equals(u.getSenha()) && u.getCodigoRedefinicaoSenha() == null));
    }

    @Test
    void listarDeveRetornarUsuarios() {
        when(usuarioRepository.findAll()).thenReturn(List.of(usuarioBase()));

        List<UsuarioDto.Response> usuarios = usuarioService.listar();

        assertEquals(1, usuarios.size());
        assertEquals("user", usuarios.get(0).getUsername());
    }

    @Test
    void buscarPorIdDeveRetornarUsuario() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioBase()));

        UsuarioDto.Response response = usuarioService.buscarPorId(1L);

        assertEquals(1L, response.getId());
        assertEquals("user", response.getUsername());
    }

    @Test
    void atualizarDevePersistirAlteracoes() {
        Usuario usuario = usuarioBase();
        UsuarioDto.UpdateRequest request = UsuarioDto.UpdateRequest.builder()
                .username("novo-nome")
                .idade(25)
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.existsByUsername("novo-nome")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.Response response = usuarioService.atualizar(1L, request);

        assertEquals("novo-nome", response.getUsername());
        assertEquals(25, response.getIdade());
    }

    @Test
    void solicitarExclusaoContaDeveEnviarCodigoPorEmail() {
        Usuario usuario = usuarioBase();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioDto.MessageResponse response = usuarioService.solicitarExclusaoConta(1L);

        assertEquals("Código de confirmação enviado para o seu e-mail.", response.getMensagem());
        verify(emailService).enviarCodigoExclusaoConta(eq("user@email.com"), eq("user"), anyString());
    }

    @Test
    void confirmarExclusaoContaDeveRemoverUsuarioQuandoCodigoValido() {
        Usuario usuario = usuarioBase();
        usuario.setCodigoExclusaoConta("123456");
        usuario.setDataExpiracaoCodigoExclusao(Instant.now().plusSeconds(3600));

        UsuarioDto.ConfirmarExclusaoRequest request = UsuarioDto.ConfirmarExclusaoRequest.builder()
                .codigo("123456")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.confirmarExclusaoConta(1L, request);

        verify(practiceSessionExerciseRepository).deleteByPracticeSessionUsuarioId(1L);
        verify(practiceSessionRepository).deleteByUsuarioId(1L);
        verify(userMissionRepository).deleteByUsuarioId(1L);
        verify(userProgressRepository).deleteByUsuarioId(1L);
        verify(userStatsRepository).deleteByUsuarioId(1L);
        verify(usuarioRepository).deleteById(1L);
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
