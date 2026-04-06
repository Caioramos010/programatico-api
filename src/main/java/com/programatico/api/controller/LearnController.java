package com.programatico.api.controller;

import com.programatico.api.dto.TrackDto;
import com.programatico.api.dto.UserMissionDto;
import com.programatico.api.dto.UserStatsDto;
import com.programatico.api.service.LearnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
