package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.dto.ContentBlockDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminContentBlockService {

    private static final Logger log = LoggerFactory.getLogger(AdminContentBlockService.class);

    private final ContentBlockRepository contentBlockRepository;
    private final ModuloRepository moduloRepository;
    private final TeoriaPaginaRepository paginaRepository;

    @Transactional(readOnly = true)
    public List<ContentBlockDto.Response> listarPorPagina(Long paginaId) {
        TeoriaPagina pagina = paginaRepository.findById(paginaId)
                .orElseThrow(() -> new ResourceNotFoundException("Página", paginaId));
        return contentBlockRepository.findByPaginaOrderByDisplayOrderAsc(pagina).stream()
                .map(ContentBlockDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public ContentBlockDto.Response criarParaPagina(Long paginaId, ContentBlockDto.Request request) {
        TeoriaPagina pagina = paginaRepository.findById(paginaId)
                .orElseThrow(() -> new ResourceNotFoundException("Página", paginaId));
        ContentBlock block = ContentBlock.builder()
                .modulo(pagina.getModulo())
                .pagina(pagina)
                .layoutType(request.getLayoutType())
                .textContent(request.getTextContent())
                .displayOrder(request.getDisplayOrder())
                .build();
        ContentBlock salvo = contentBlockRepository.save(block);
        log.info("ContentBlock criado para pagina: id={}, paginaId={}", salvo.getId(), paginaId);
        return ContentBlockDto.Response.fromEntity(salvo);
    }

    @Transactional(readOnly = true)
    public List<ContentBlockDto.Response> listarPorModulo(Long moduloId) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        return contentBlockRepository.findByModuloOrderByDisplayOrderAsc(modulo).stream()
                .map(ContentBlockDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public ContentBlockDto.Response criar(Long moduloId, ContentBlockDto.Request request) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        ContentBlock block = ContentBlock.builder()
                .modulo(modulo)
                .layoutType(request.getLayoutType())
                .textContent(request.getTextContent())
                .displayOrder(request.getDisplayOrder())
                .build();
        ContentBlock salvo = contentBlockRepository.save(block);
        log.info("ContentBlock criado: id={}, type={}", salvo.getId(), salvo.getLayoutType());
        return ContentBlockDto.Response.fromEntity(salvo);
    }

    @Transactional
    public ContentBlockDto.Response atualizar(Long id, ContentBlockDto.Request request) {
        ContentBlock block = contentBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloco de conteúdo", id));
        block.setLayoutType(request.getLayoutType());
        block.setTextContent(request.getTextContent());
        block.setDisplayOrder(request.getDisplayOrder());
        return ContentBlockDto.Response.fromEntity(contentBlockRepository.save(block));
    }

    @Transactional
    public void deletar(Long id) {
        if (!contentBlockRepository.existsById(id)) {
            throw new ResourceNotFoundException("Bloco de conteúdo", id);
        }
        contentBlockRepository.deleteById(id);
        log.info("ContentBlock deletado: id={}", id);
    }
}
