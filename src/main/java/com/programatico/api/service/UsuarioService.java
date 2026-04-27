package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final int TAMANHO_CODIGO = 6;
    private static final int EXPIRACAO_CODIGO_HORAS = 24;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Transactional
    public UsuarioDto.LoginResponse login(UsuarioDto.LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailOrUsername(request.getEmailOuUsername(), request.getEmailOuUsername())
                .orElseThrow(() -> new BadRequestException("E-mail/usuário ou senha inválidos"));
        if (!usuario.getAtivo()) {
            throw new BadRequestException("Conta não ativada. Verifique seu e-mail para o código de ativação.");
        }
        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new BadRequestException("E-mail/usuário ou senha inválidos");
        }
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
                .codigoAtivacao(codigoAtivacao)
                .dataCriacao(agora)
                .dataAtualizacao(agora)
                .build();
        usuario = usuarioRepository.save(usuario);
        emailService.enviarCodigoAtivacao(usuario.getEmail(), usuario.getUsername(), codigoAtivacao);
        return UsuarioDto.Response.fromEntity(usuario);
    }

    @Transactional
    public UsuarioDto.MessageResponse ativar(UsuarioDto.AtivacaoRequest request) {
        Usuario usuario = usuarioRepository.findByCodigoAtivacao(request.getCodigo())
                .orElseThrow(() -> new BadRequestException("Código de ativação inválido"));
        usuario.setAtivo(true);
        usuario.setCodigoAtivacao(null);
        usuarioRepository.save(usuario);
        return UsuarioDto.MessageResponse.of("Conta ativada com sucesso. Faça login.");
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarAtivacao(UsuarioDto.SolicitarAtivacaoRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Se o e-mail existir, você receberá um novo código de ativação."));
        if (Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new BadRequestException("Conta já ativada. Faça login.");
        }
        String codigoAtivacao = gerarCodigo();
        usuario.setCodigoAtivacao(codigoAtivacao);
        usuarioRepository.save(usuario);
        emailService.enviarCodigoAtivacao(usuario.getEmail(), usuario.getUsername(), codigoAtivacao);
        return UsuarioDto.MessageResponse.of("Se o e-mail existir, você receberá um novo código de ativação.");
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarRedefinicaoSenha(UsuarioDto.SolicitarRedefinicaoSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Se o e-mail existir, você receberá um código para redefinir a senha."));
        String codigo = gerarCodigo();
        usuario.setCodigoRedefinicaoSenha(codigo);
        usuario.setDataExpiracaoCodigoRedefinicao(Instant.now().plus(EXPIRACAO_CODIGO_HORAS, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);
        emailService.enviarCodigoRedefinicaoSenha(usuario.getEmail(), usuario.getUsername(), codigo);
        return UsuarioDto.MessageResponse.of("Se o e-mail existir, você receberá um código para redefinir a senha.");
    }

    @Transactional
    public UsuarioDto.MessageResponse redefinirSenha(UsuarioDto.NovaSenhaRequest request) {
        Usuario usuario = usuarioRepository.findByCodigoRedefinicaoSenha(request.getCodigo())
                .orElseThrow(() -> new BadRequestException("Código inválido ou expirado"));
        if (usuario.getDataExpiracaoCodigoRedefinicao() == null || usuario.getDataExpiracaoCodigoRedefinicao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo.");
        }
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setCodigoRedefinicaoSenha(null);
        usuario.setDataExpiracaoCodigoRedefinicao(null);
        usuarioRepository.save(usuario);
        return UsuarioDto.MessageResponse.of("Senha alterada com sucesso. Faça login.");
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto.Response> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioDto.Response::fromEntity)
                .collect(Collectors.toList());
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
    public void excluir(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuário", id);
        }
        usuarioRepository.deleteById(id);
    }

    @Transactional
    public UsuarioDto.MessageResponse solicitarExclusaoConta(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        String codigo = gerarCodigo();
        usuario.setCodigoExclusaoConta(codigo);
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
                || !usuario.getCodigoExclusaoConta().equals(request.getCodigo())) {
            throw new BadRequestException("Código inválido");
        }
        if (usuario.getDataExpiracaoCodigoExclusao() == null
                || usuario.getDataExpiracaoCodigoExclusao().isBefore(Instant.now())) {
            throw new BadRequestException("Código expirado. Solicite um novo.");
        }
        usuarioRepository.deleteById(id);
    }

    private static String gerarCodigo() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
