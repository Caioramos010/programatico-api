package com.programatico.api.controller;

import com.programatico.api.dto.ReviewDto;
import com.programatico.api.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ReviewDto.Response> getReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(reviewService.getReview(userDetails.getUsername(), trackId, days));
    }
}
