package com.programatico.api.controller;

import com.programatico.api.dto.TheoryDto;
import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.service.LearnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aprender")
@RequiredArgsConstructor
public class LearnController {

    private final LearnService learnService;

    @GetMapping("/trilha")
    public ResponseEntity<TrackDto.Response> getTrilha(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(learnService.getTrilhaComProgresso(userDetails.getUsername()));
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsDto.Response> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(learnService.getEstatisticas(userDetails.getUsername()));
    }

    @GetMapping("/missoes")
    public ResponseEntity<List<UserMissionDto.Response>> getMissoes(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(learnService.getMissoes(userDetails.getUsername()));
    }

    @GetMapping("/modulos/{moduloId}/teorico")
    public ResponseEntity<TheoryDto.Response> getTeorico(
            @PathVariable Long moduloId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(learnService.getTeorico(moduloId, userDetails.getUsername()));
    }

    @PostMapping("/modulos/{moduloId}/teorico/concluir")
    public ResponseEntity<Map<String, Boolean>> concluirTeorico(
            @PathVariable Long moduloId,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean firstCompletion = learnService.concluirTeorico(moduloId, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("firstCompletion", firstCompletion));
    }
}
