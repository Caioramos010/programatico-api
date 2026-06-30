package com.programatico.api.controller;

import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.dto.NotificationDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void listarDeveRetornar200() throws Exception {
        when(notificationService.listarPorUsuario("user")).thenReturn(List.of(
                NotificationDto.Response.builder()
                        .id(1L)
                        .title("Sem vidas")
                        .message("Aguarde recarga")
                        .kind(NotificationKind.EXERCICIO)
                        .read(false)
                        .build()));

        mockMvc.perform(get("/api/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sem vidas"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @WithMockUser(username = "user")
    void marcarComoLidaDeveRetornar200() throws Exception {
        when(notificationService.marcarComoLida(5L, "user"))
                .thenReturn(NotificationDto.Response.builder().id(5L).read(true).build());

        mockMvc.perform(patch("/api/notificacoes/5/marcar-como-lida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "user")
    void marcarTodasComoLidasDeveRetornar204() throws Exception {
        doNothing().when(notificationService).marcarTodasComoLidas("user");

        mockMvc.perform(patch("/api/notificacoes/marcar-todas-como-lidas"))
                .andExpect(status().isNoContent());
    }
}
