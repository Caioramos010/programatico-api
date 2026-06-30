package com.programatico.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.dto.SettingsDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.TotpSettingsService;
import com.programatico.api.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserSettingsService userSettingsService;
    @MockitoBean private TotpSettingsService totpSettingsService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    void obterNotificacoesSemAutenticacaoDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/configuracoes/notificacoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void obterNotificacoesDeveRetornar200() throws Exception {
        when(userSettingsService.obterPreferenciasNotificacao("user"))
                .thenReturn(SettingsDto.NotificationPreferencesResponse.builder()
                        .disableAllNotifications(false)
                        .build());

        mockMvc.perform(get("/api/configuracoes/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(false));
    }

    @Test
    @WithMockUser(username = "user")
    void atualizarNotificacoesDeveRetornar200() throws Exception {
        when(userSettingsService.atualizarPreferenciasNotificacao(eq("user"), any()))
                .thenReturn(SettingsDto.NotificationPreferencesResponse.builder()
                        .disableAllNotifications(true)
                        .build());

        mockMvc.perform(put("/api/configuracoes/notificacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disableUpdateNotifications": false,
                                  "disableDaystreakNotifications": false,
                                  "disableMissionNotifications": false,
                                  "disableSubscriptionNotifications": false,
                                  "disableEmailNotifications": false,
                                  "disableAllNotifications": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disableAllNotifications").value(true));
    }

    @Test
    @WithMockUser(username = "user")
    void atualizarSegurancaDeveRetornar200() throws Exception {
        when(userSettingsService.atualizarPreferenciasSeguranca(eq("user"), any()))
                .thenReturn(SettingsDto.SecurityPreferencesResponse.builder()
                        .twoFactorEnabled(true)
                        .backupCodes(List.of("ABCD-2345"))
                        .backupCodesRemaining(1)
                        .build());

        mockMvc.perform(put("/api/configuracoes/seguranca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twoFactorEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.backupCodesRemaining").value(1));
    }

    @Test
    @WithMockUser(username = "user")
    void configurarTotpDeveRetornarSetup() throws Exception {
        when(totpSettingsService.iniciarSetup("user"))
                .thenReturn(SettingsDto.TotpSetupResponse.builder()
                        .secret("SECRET123")
                        .otpauthUrl("otpauth://totp/Programatico:user@test.com?secret=SECRET123")
                        .qrCodeDataUrl("data:image/png;base64,abc")
                        .build());

        mockMvc.perform(post("/api/configuracoes/totp/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRET123"))
                .andExpect(jsonPath("$.qrCodeDataUrl").value("data:image/png;base64,abc"));
    }
}
