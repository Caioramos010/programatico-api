package com.programatico.api.service;

import com.programatico.api.domain.Track;
import com.programatico.api.dto.TrilhaDto;
import com.programatico.api.exception.ResourceNotFoundException;
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
public class AdminTrilhaService {

    private static final Logger log = LoggerFactory.getLogger(AdminTrilhaService.class);

    private final TrackRepository trackRepository;
    private final ModuloRepository moduloRepository;

    @Transactional(readOnly = true)
    public List<TrilhaDto.Response> listarTodas() {
        return trackRepository.findAll().stream()
                .map(track -> TrilhaDto.Response.fromEntity(track, moduloRepository.countByTrack(track)))
                .toList();
    }

    @Transactional
    public TrilhaDto.Response criar(TrilhaDto.Request request) {
        int proximaOrdem = (int) (trackRepository.count() + 1);
        Track track = Track.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .displayOrder(proximaOrdem)
                .icon(request.getIcon())
                .build();
        Track salva = trackRepository.save(track);
        log.info("Trilha criada: id={}, title={}", salva.getId(), salva.getTitle());
        return TrilhaDto.Response.fromEntity(salva, 0L);
    }

    @Transactional
    public TrilhaDto.Response atualizar(Long id, TrilhaDto.Request request) {
        Track track = trackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha", id));
        track.setTitle(request.getTitle());
        track.setDescription(request.getDescription());
        track.setIcon(request.getIcon());
        Track salva = trackRepository.save(track);
        return TrilhaDto.Response.fromEntity(salva, moduloRepository.countByTrack(salva));
    }

    @Transactional
    public void deletar(Long id) {
        if (!trackRepository.existsById(id)) {
            throw new ResourceNotFoundException("Trilha", id);
        }
        trackRepository.deleteById(id);
        log.info("Trilha deletada: id={}", id);
    }
}
