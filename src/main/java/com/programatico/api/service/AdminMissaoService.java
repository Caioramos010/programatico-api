package com.programatico.api.service;

import com.programatico.api.domain.Mission;
import com.programatico.api.dto.MissaoDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMissaoService {

    private static final Logger log = LoggerFactory.getLogger(AdminMissaoService.class);

    private final MissionRepository missionRepository;

    @Transactional(readOnly = true)
    public List<MissaoDto.Response> listarTodas() {
        return missionRepository.findAll().stream()
                .map(MissaoDto.Response::fromEntity)
                .toList();
    }

    @Transactional
    public MissaoDto.Response criar(MissaoDto.Request request) {
        Mission mission = Mission.builder()
                .title(request.getTitle())
                .objectiveType(request.getObjectiveType())
                .xpReward(request.getXpReward())
                .quantidade(request.getQuantidade())
                .build();
        Mission salva = missionRepository.save(mission);
        log.info("Missão criada: id={}, type={}", salva.getId(), salva.getObjectiveType());
        return MissaoDto.Response.fromEntity(salva);
    }

    @Transactional
    public MissaoDto.Response atualizar(Long id, MissaoDto.Request request) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Missão", id));
        mission.setTitle(request.getTitle());
        mission.setObjectiveType(request.getObjectiveType());
        mission.setXpReward(request.getXpReward());
        mission.setQuantidade(request.getQuantidade());
        return MissaoDto.Response.fromEntity(missionRepository.save(mission));
    }

    @Transactional
    public void deletar(Long id) {
        if (!missionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Missão", id);
        }
        missionRepository.deleteById(id);
        log.info("Missão deletada: id={}", id);
    }
}
