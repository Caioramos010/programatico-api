package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.dto.TeoriaPaginaDto;
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
public class AdminTeoriaPaginaService {

    private static final Logger log = LoggerFactory.getLogger(AdminTeoriaPaginaService.class);

    private final TeoriaPaginaRepository paginaRepository;
    private final ModuloRepository moduloRepository;
    private final ContentBlockRepository contentBlockRepository;

    @Transactional(readOnly = true)
    public List<TeoriaPaginaDto.Response> listar(Long moduloId) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        return paginaRepository.findByModuloOrderByDisplayOrderAsc(modulo).stream()
                .map(p -> TeoriaPaginaDto.Response.fromEntity(p, contentBlockRepository.countByPagina(p)))
                .toList();
    }

    @Transactional
    public TeoriaPaginaDto.Response criar(Long moduloId, TeoriaPaginaDto.Request request) {
        Modulo modulo = moduloRepository.findById(moduloId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", moduloId));
        int proximaOrdem = (int) (paginaRepository.countByModulo(modulo) + 1);
        TeoriaPagina pagina = TeoriaPagina.builder()
                .modulo(modulo)
                .title(request.getTitle())
                .description(request.getDescription())
                .displayOrder(proximaOrdem)
                .build();
        TeoriaPagina salva = paginaRepository.save(pagina);
        log.info("TeoriaPagina criada: id={}, moduloId={}", salva.getId(), moduloId);
        return TeoriaPaginaDto.Response.fromEntity(salva, 0);
    }

    @Transactional
    public TeoriaPaginaDto.Response atualizar(Long id, TeoriaPaginaDto.Request request) {
        TeoriaPagina pagina = paginaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Página", id));
        pagina.setTitle(request.getTitle());
        pagina.setDescription(request.getDescription());
        TeoriaPagina salva = paginaRepository.save(pagina);
        long totalBlocos = contentBlockRepository.countByPagina(salva);
        return TeoriaPaginaDto.Response.fromEntity(salva, totalBlocos);
    }

    @Transactional
    public void deletar(Long id) {
        if (!paginaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Página", id);
        }
        paginaRepository.deleteById(id);
        log.info("TeoriaPagina deletada: id={}", id);
    }
}
