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

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminDashboardService adminDashboardService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void getMetricasDeveRetornar200() throws Exception {
        when(adminDashboardService.getMetricas()).thenReturn(AdminDashboardDto.Response.builder()
                .totalUsers(10L)
                .totalModules(5L)
                .build());

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.totalModules").value(5));
    }
}
