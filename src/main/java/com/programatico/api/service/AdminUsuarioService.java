package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.dto.AdminUsuarioDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(AdminUsuarioService.class);

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<AdminUsuarioDto.Response> listarTodos(String busca) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        return usuarios.stream()
                .filter(u -> u.getDeletedAt() == null)
                .filter(u -> busca == null || busca.isBlank()
                        || u.getUsername().toLowerCase().contains(busca.toLowerCase())
                        || u.getEmail().toLowerCase().contains(busca.toLowerCase()))
                .map(AdminUsuarioDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public AdminUsuarioDto.Response atualizar(Long id, AdminUsuarioDto.UpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        usuario.setRole(request.getRole());
        usuario.setAtivo(request.getAtivo());
        return AdminUsuarioDto.Response.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional
    public void deletar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
        // Exclusão lógica: a conta some da listagem mas os dados permanecem.
        // Se o usuário fizer login novamente, deletedAt é limpo e a conta volta.
        usuario.setDeletedAt(Instant.now());
        usuarioRepository.save(usuario);
        log.info("Usuário desativado (exclusão lógica) pelo admin: id={}", id);
    }
}
