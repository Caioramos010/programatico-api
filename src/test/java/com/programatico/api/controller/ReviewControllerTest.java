package com.programatico.api.controller;

import com.programatico.api.dto.ReviewDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void getReviewDeveRetornar200ComPayloadAgregado() throws Exception {
        ReviewDto.Response response = ReviewDto.Response.builder()
                .selectedTrackId(1L)
                .selectedDays(7)
                .currentXp(250)
                .availableTracks(List.of(ReviewDto.TrackOption.builder().id(1L).title("Logica Basica").build()))
                .stats(List.of(ReviewDto.StatCard.builder()
                        .title("Exercicios feitos")
                        .value("2")
                        .subtitle("No periodo selecionado")
                        .build()))
                .performanceData(List.of(ReviewDto.PerformancePoint.builder()
                        .day("Seg")
                        .acertos(1)
                        .erros(1)
                        .build()))
                .subjectAccuracy(List.of(ReviewDto.SubjectAccuracyItem.builder()
                        .assunto("Fluxo logico")
                        .percentual(50)
                        .color("#f27584")
                        .build()))
                .errorsBySubject(List.of(ReviewDto.ErrorBySubjectItem.builder()
                        .assunto("Fluxo logico")
                        .erros(1)
                        .build()))
                .reviewNow(List.of(ReviewDto.ReviewNowItem.builder()
                        .assunto("Fluxo logico")
                        .build()))
                .recentMissions(List.of(ReviewDto.RecentMissionItem.builder()
                        .label("Treinar logica")
                        .status("Em progresso (2/3)")
                        .build()))
                .build();

        when(reviewService.getReview(anyString(), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/review")
                        .param("trackId", "1")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedTrackId").value(1))
                .andExpect(jsonPath("$.selectedDays").value(7))
                .andExpect(jsonPath("$.stats[0].title").value("Exercicios feitos"))
                .andExpect(jsonPath("$.recentMissions[0].label").value("Treinar logica"));
    }

    @Test
    @WithMockUser(username = "user")
    void getReviewSemParametrosDeveRetornar200() throws Exception {
        when(reviewService.getReview(anyString(), any(), any())).thenReturn(
                ReviewDto.Response.builder()
                        .selectedDays(7)
                        .currentXp(0)
                        .stats(List.of())
                        .performanceData(List.of())
                        .availableTracks(List.of())
                        .subjectAccuracy(List.of())
                        .errorsBySubject(List.of())
                        .reviewNow(List.of())
                        .recentMissions(List.of())
                        .build());

        mockMvc.perform(get("/api/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedDays").value(7));
    }
}
