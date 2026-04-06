package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.ModuloDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminModuloService {

    private static final Logger log = LoggerFactory.getLogger(AdminModuloService.class);

    private final ModuloRepository moduloRepository;
    private final TrackRepository trackRepository;
    private final ExerciseRepository exerciseRepository;
    private final ContentBlockRepository contentBlockRepository;

    @Transactional(readOnly = true)
    public List<ModuloDto.Response> listarPorTrilha(Long trilhaId) {
        Track track = trackRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha", trilhaId));
        return moduloRepository.findByTrackOrderByDisplayOrderAsc(track).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional
    public ModuloDto.Response criar(Long trilhaId, ModuloDto.Request request) {
        Track track = trackRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha", trilhaId));
        int proximaOrdem = (int) (moduloRepository.countByTrack(track) + 1);
        Modulo modulo = Modulo.builder()
                .track(track)
                .title(request.getTitle())
                .moduleType(request.getModuleType())
                .displayOrder(proximaOrdem)
                .description(request.getDescription())
                .build();
        Modulo salvo = moduloRepository.save(modulo);
        log.info("Módulo criado: id={}, title={}, type={}", salvo.getId(), salvo.getTitle(), salvo.getModuleType());
        return ModuloDto.Response.fromEntity(salvo, 0L, 0L);
    }

    @Transactional
    public ModuloDto.Response atualizar(Long id, ModuloDto.Request request) {
        Modulo modulo = moduloRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo", id));
        modulo.setTitle(request.getTitle());
        modulo.setModuleType(request.getModuleType());
        modulo.setDescription(request.getDescription());
        Modulo salvo = moduloRepository.save(modulo);
        return buildResponse(salvo);
    }

    @Transactional
    public void reordenar(Long trilhaId, List<Long> ids) {
        Track track = trackRepository.findById(trilhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha", trilhaId));
        List<Modulo> modulos = moduloRepository.findByTrackOrderByDisplayOrderAsc(track);
        for (int i = 0; i < ids.size(); i++) {
            final int ordem = i + 1;
            final Long id = ids.get(i);
            modulos.stream()
                    .filter(m -> m.getId().equals(id))
                    .findFirst()
                    .ifPresent(m -> m.setDisplayOrder(ordem));
        }
        moduloRepository.saveAll(modulos);
        log.info("Módulos reordenados para trilha id={}", trilhaId);
    }

    @Transactional
    public void deletar(Long id) {
        if (!moduloRepository.existsById(id)) {
            throw new ResourceNotFoundException("Módulo", id);
        }
        moduloRepository.deleteById(id);
        log.info("Módulo deletado: id={}", id);
    }

    private ModuloDto.Response buildResponse(Modulo m) {
        long total;
        long xp;
        if (m.getModuleType() == ModuleType.ACTIVITY) {
            total = exerciseRepository.countByModulo(m);
            xp = exerciseRepository.sumXpByModulo(m);
        } else {
            total = contentBlockRepository.countByModulo(m);
            xp = 0L;
        }
        return ModuloDto.Response.fromEntity(m, total, xp);
    }
}
