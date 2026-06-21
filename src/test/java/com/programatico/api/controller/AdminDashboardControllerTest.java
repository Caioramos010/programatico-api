package com.programatico.api.controller;

import com.programatico.api.dto.AdminDashboardDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void getMetricasDeveRetornar200ComMetricas() throws Exception {
        AdminDashboardDto.Response response = AdminDashboardDto.Response.builder()
                .totalUsers(42L)
                .activeSessions(7L)
                .totalModules(13L)
                .growthPercent(15)
                .build();

        when(adminDashboardService.getMetricas()).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(42))
                .andExpect(jsonPath("$.activeSessions").value(7))
                .andExpect(jsonPath("$.totalModules").value(13))
                .andExpect(jsonPath("$.growthPercent").value(15));
    }
}
