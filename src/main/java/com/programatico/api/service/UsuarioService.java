package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.NotificationRepository;
import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.PracticeSessionExerciseRepository;
import com.programatico.api.repository.PracticeSessionRepository;
import com.programatico.api.repository.UserDailyMissionRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UserProgressRepository;
import com.programatico.api.repository.UserStatsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.programatico.api.util.CodigoAcesso;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final int TAMANHO_CODIGO = 6;
    private static final int EXPIRACAO_CODIGO_HORAS = 24;
    private static final int EXPIRACAO_CODIGO_LOGIN_HORAS = 1;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final UserDailyMissionRepository userDailyMissionRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStatsRepository userStatsRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeSessionExerciseRepository practiceSessionExerciseRepository;
    private final VerificationCodeGuardService verificationCodeGuardService;
    private final TrustedDeviceService trustedDeviceService;

    private Usuario validarCredenciaisLogin(String emailOuUsername, String senha) {
        Usuario usuario = usuarioRepository.findByEmailOrUsername(emailOuUsername, emailOuUsername)
                .orElseThrow(() -> new BadRequestException("E-mail/usuário ou senha inválidos"));
        if (!usuario.getAtivo()) {
            throw new BadRequestException("Conta não ativada. Verifique seu e-mail para o código de ativação.");
        }
        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new BadRequestException("E-mail/usuário ou senha inválidos");
        }
        return usuario;
    }

    @Transactional
    public UsuarioDto.LoginIniciarResponse iniciarLogin(UsuarioDto.LoginRequest request, String trustedDeviceToken) {
        Usuario usuario = validarCredenciaisLogin(request.getEmailOuUsername(), request.getSenha());
        if (trustedDeviceService.isConfiavel(usuario.getId(), trustedDeviceToken)) {
            return UsuarioDto.LoginIniciarResponse.loginDireto(finalizarLogin(usuario));
        }
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
        String codigo = gerarCodigo();
        usuario.setCodigoVerificacaoLogin(CodigoAcesso.hash(codigo));
        usuario.setDataExpiracaoCodigoLogin(Instant.now().plus(EXPIRACAO_CODIGO_LOGIN_HORAS, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);
        emailService.enviarCodigoVerificacaoLogin(usuario.getEmail(), usuario.getUsername(), codigo);
        return UsuarioDto.LoginIniciarResponse.comVerificacao(
                "Enviamos um código de verificação para o seu e-mail.");
    }

    @Transactional
    public UsuarioDto.LoginResponse confirmarLogin(UsuarioDto.LoginConfirmarRequest request) {
        Usuario usuario = validarCredenciaisLogin(request.getEmailOuUsername(), request.getSenha());
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.LOGIN);
        if (usuario.getCodigoVerificacaoLogin() == null
                || !usuario.getCodigoVerificacaoLogin().equals(CodigoAcesso.hash(request.getCodigo()))) {
            verificationCodeGuardService.recordFailedAttempt(usuario, VerificationCodeContext.LOGIN);
        }
        if (usuario.getDataExpiracaoCodigoLogin() == null
                || usuario.getDataExpiracaoCodigoLogin().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Volte à tela de login e tente novamente.");
        }
        verificationCodeGuardService.resetAttempts(usuario, VerificationCodeContext.LOGIN);
        usuario.setCodigoVerificacaoLogin(null);
        usuario.setDataExpiracaoCodigoLogin(null);
        return finalizarLogin(usuario);
    }

    private UsuarioDto.LoginResponse finalizarLogin(Usuario usuario) {
        // Conta excluída logicamente pelo admin volta ao fazer login com sucesso.
        usuario.setDeletedAt(null);
        usuarioRepository.save(usuario);
        String token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
        return new UsuarioDto.LoginResponse(token, UsuarioDto.Response.fromEntity(usuario));
    }

    @Transactional
    public UsuarioDto.Response registro(UsuarioDto.RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("E-mail já cadastrado");
        }
        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Nome de usuário já em uso");
        }
        String codigoAtivacao = gerarCodigo();
        Instant agora = Instant.now();
        Usuario usuario = Usuario.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .senha(passwordEncoder.encode(request.getSenha()))
                .idade(request.getIdade())
                .ativo(false)
                .codigoAtivacao(CodigoAcesso.hash(codigoAtivacao))
                .dataExpiracaoCodigoAtivacao(agora.plus(EXPIRACAO_CODIGO_HORAS, ChronoUnit.HOURS))
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();
        usuario = usuarioRepository.save(usuario);
        emailService.enviarCodigoAtivacao(usuario.getEmail(), usuario.getUsername(), codigoAtivacao);
        return UsuarioDto.Response.fromEntity(usuario);
    }

    @Transactional
    public UsuarioDto.LoginResponse ativar(UsuarioDto.AtivacaoRequest request) {
        if (StringUtils.hasText(request.getEmail())) {
            return ativarComEmail(request.getEmail().trim(), request.getCodigo().trim());
        }
        Usuario usuario = usuarioRepository.findByCodigoAtivacao(CodigoAcesso.hash(request.getCodigo()))
                .orElseThrow(() -> new BadRequestException("Código de ativação inválido"));
        return concluirAtivacao(usuario);
    }

    private UsuarioDto.LoginResponse ativarComEmail(String email, String codigo) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Código de ativação inválido"));
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.ACTIVATION);
        if (Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new BadRequestException("Conta já ativada. Faça login.");
        }
        if (usuario.getCodigoAtivacao() == null
                || !usuario.getCodigoAtivacao().equals(CodigoAcesso.hash(codigo))) {
            verificationCodeGuardService.recordFailedAttempt(usuario, VerificationCodeContext.ACTIVATION);
        }
        return concluirAtivacao(usuario);
    }

    private UsuarioDto.LoginResponse concluirAtivacao(Usuario usuario) {
        // null = código emitido antes da política de expiração (compatibilidade).
        if (usuario.getDataExpiracaoCodigoAtivacao() != null
                && usuario.getDataExpiracaoCodigoAtivacao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo código de ativação.");
        }
        verificationCodeGuardService.resetAttempts(usuario, VerificationCodeContext.ACTIVATION);
        usuario.setAtivo(true);
        usuario.setCodigoAtivacao(null);
        usuario.setDataExpiracaoCodigoAtivacao(null);
        usuarioRepository.save(usuario);
        // O código de ativação prova posse do e-mail — mesmo nível de confiança da
        // verificação de login, então a conta já sai logada (sem pedir outro código).
        String token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
        return new UsuarioDto.LoginResponse(token, UsuarioDto.Response.fromEntity(usuario));
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarAtivacao(UsuarioDto.SolicitarAtivacaoRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Se o e-mail existir, você receberá um novo código de ativação."));
        if (Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new BadRequestException("Conta já ativada. Faça login.");
        }
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.ACTIVATION);
        String codigoAtivacao = gerarCodigo();
        usuario.setCodigoAtivacao(CodigoAcesso.hash(codigoAtivacao));
        usuario.setDataExpiracaoCodigoAtivacao(Instant.now().plus(EXPIRACAO_CODIGO_HORAS, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);
        emailService.enviarCodigoAtivacao(usuario.getEmail(), usuario.getUsername(), codigoAtivacao);
        return UsuarioDto.MessageResponse.of("Se o e-mail existir, você receberá um novo código de ativação.");
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarRedefinicaoSenha(UsuarioDto.SolicitarRedefinicaoSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Se o e-mail existir, você receberá um código para redefinir a senha."));
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.PASSWORD_RESET);
        String codigo = gerarCodigo();
        usuario.setCodigoRedefinicaoSenha(CodigoAcesso.hash(codigo));
        usuario.setDataExpiracaoCodigoRedefinicao(Instant.now().plus(EXPIRACAO_CODIGO_HORAS, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);
        emailService.enviarCodigoRedefinicaoSenha(usuario.getEmail(), usuario.getUsername(), codigo);
        return UsuarioDto.MessageResponse.of("Se o e-mail existir, você receberá um código para redefinir a senha.");
    }

    @Transactional
    public UsuarioDto.MessageResponse redefinirSenha(UsuarioDto.NovaSenhaRequest request) {
        if (StringUtils.hasText(request.getEmail())) {
            return redefinirSenhaComEmail(request.getEmail().trim(), request.getCodigo().trim(), request.getNovaSenha());
        }
        Usuario usuario = usuarioRepository.findByCodigoRedefinicaoSenha(CodigoAcesso.hash(request.getCodigo()))
                .orElseThrow(() -> new BadRequestException("Código inválido ou expirado"));
        return concluirRedefinicaoSenha(usuario, request.getNovaSenha());
    }

    private UsuarioDto.MessageResponse redefinirSenhaComEmail(String email, String codigo, String novaSenha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Código inválido ou expirado"));
        verificationCodeGuardService.ensureNotBlocked(usuario, VerificationCodeContext.PASSWORD_RESET);
        if (usuario.getCodigoRedefinicaoSenha() == null
                || !usuario.getCodigoRedefinicaoSenha().equals(CodigoAcesso.hash(codigo))) {
            verificationCodeGuardService.recordFailedAttempt(usuario, VerificationCodeContext.PASSWORD_RESET);
        }
        if (usuario.getDataExpiracaoCodigoRedefinicao() == null
                || usuario.getDataExpiracaoCodigoRedefinicao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo.");
        }
        return concluirRedefinicaoSenha(usuario, novaSenha);
    }

    private UsuarioDto.MessageResponse concluirRedefinicaoSenha(Usuario usuario, String novaSenha) {
        if (usuario.getDataExpiracaoCodigoRedefinicao() == null
                || usuario.getDataExpiracaoCodigoRedefinicao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo.");
        }
        verificationCodeGuardService.resetAttempts(usuario, VerificationCodeContext.PASSWORD_RESET);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setCodigoRedefinicaoSenha(null);
        usuario.setDataExpiracaoCodigoRedefinicao(null);
        usuarioRepository.save(usuario);
        return UsuarioDto.MessageResponse.of("Senha alterada com sucesso. Faça login.");
    }

    /**
     * Garante que o recurso {id} pertence ao usuário autenticado (do token).
     * Em divergência, responde como "não encontrado" para não revelar a existência de contas de terceiros.
     */
    @Transactional(readOnly = true)
    public void verificarProprioRecurso(Long id, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado para o token informado."));
        if (!usuario.getId().equals(id)) {
            throw new ResourceNotFoundException("Usuário", id);
        }
    }

    @Transactional(readOnly = true)
    public UsuarioDto.Response buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        return UsuarioDto.Response.fromEntity(usuario);
    }

    @Transactional
    public UsuarioDto.Response atualizar(Long id, UsuarioDto.UpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(usuario.getUsername()) && usuarioRepository.existsByUsername(request.getUsername())) {
                throw new BadRequestException("Nome de usuário já em uso");
            }
            usuario.setUsername(request.getUsername());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (!request.getEmail().equals(usuario.getEmail()) && usuarioRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("E-mail já cadastrado");
            }
            usuario.setEmail(request.getEmail());
        }
        if (request.getSenha() != null && !request.getSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        }
        if (request.getIdade() != null) {
            usuario.setIdade(request.getIdade());
        }
        if (request.getNivelHabilidade() != null) {
            usuario.setNivelHabilidade(request.getNivelHabilidade());
        }
        if (request.getIcon() != null) {
            usuario.setIcon(request.getIcon());
        }
        usuario = usuarioRepository.save(usuario);
        return UsuarioDto.Response.fromEntity(usuario);
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarExclusaoConta(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        String codigo = gerarCodigo();
        usuario.setCodigoExclusaoConta(CodigoAcesso.hash(codigo));
        usuario.setDataExpiracaoCodigoExclusao(Instant.now().plus(EXPIRACAO_CODIGO_HORAS, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);
        emailService.enviarCodigoExclusaoConta(usuario.getEmail(), usuario.getUsername(), codigo);
        return UsuarioDto.MessageResponse.of("Código de confirmação enviado para o seu e-mail.");
    }

    @Transactional
    public void confirmarExclusaoConta(Long id, UsuarioDto.ConfirmarExclusaoRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        if (usuario.getCodigoExclusaoConta() == null
                || !usuario.getCodigoExclusaoConta().equals(CodigoAcesso.hash(request.getCodigo()))) {
            throw new BadRequestException("Código inválido");
        }
        if (usuario.getDataExpiracaoCodigoExclusao() == null
                || usuario.getDataExpiracaoCodigoExclusao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo.");
        }
        excluirRegistrosVinculados(id);
        usuarioRepository.deleteById(id);
    }

    /**
     * Remove TODOS os registros com FK para o usuário antes do hard delete —
     * qualquer tabela esquecida aqui bloqueia a exclusão de conta com violação
     * de integridade (FKs geradas pelo Hibernate não têm ON DELETE CASCADE).
     */
    private void excluirRegistrosVinculados(Long userId) {
        practiceSessionExerciseRepository.deleteByPracticeSessionUsuarioId(userId);
        practiceSessionRepository.deleteByUsuarioId(userId);
        userDailyMissionRepository.deleteByUsuarioId(userId);
        notificationRepository.deleteByUsuarioId(userId);
        paymentRepository.deleteByUsuarioId(userId);
        userSettingsRepository.deleteByUsuarioId(userId);
        userProgressRepository.deleteByUsuarioId(userId);
        userStatsRepository.deleteByUsuarioId(userId);
    }

    private static String gerarCodigo() {
        return CodigoAcesso.gerar(TAMANHO_CODIGO);
    }
}
