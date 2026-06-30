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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "user")
    void listarPorUsuarioDeveRetornar200ComLista() throws Exception {
        NotificationDto.Response response = notificacaoBase(1L, false);
        when(notificationService.listarPorUsuario(anyString())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Nova trilha desbloqueada"))
                .andExpect(jsonPath("$[0].kind").value("TRILHA"));
    }

    @Test
    @WithMockUser(username = "user")
    void buscarPorIdDeveRetornar200ComNotificacao() throws Exception {
        when(notificationService.buscarPorId(anyLong(), anyString())).thenReturn(notificacaoBase(2L, true));

        mockMvc.perform(get("/api/notificacoes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "user")
    void marcarComoLidaDeveRetornar200ComNotificacaoAtualizada() throws Exception {
        when(notificationService.marcarComoLida(anyLong(), anyString())).thenReturn(notificacaoBase(3L, true));

        mockMvc.perform(patch("/api/notificacoes/3/marcar-como-lida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "user")
    void marcarTodasComoLidasDeveRetornar204() throws Exception {
        doNothing().when(notificationService).marcarTodasComoLidas(anyString());

        mockMvc.perform(patch("/api/notificacoes/marcar-todas-como-lidas"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user")
    void excluirDeveRetornar204() throws Exception {
        doNothing().when(notificationService).excluir(anyLong(), anyString());

        mockMvc.perform(delete("/api/notificacoes/4"))
                .andExpect(status().isNoContent());
    }

    private NotificationDto.Response notificacaoBase(Long id, boolean read) {
        return NotificationDto.Response.builder()
                .id(id)
                .title("Nova trilha desbloqueada")
                .message("Voce desbloqueou mais uma trilha")
                .kind(NotificationKind.TRILHA)
                .read(read)
                .createdAt(Instant.now())
                .readAt(read ? Instant.now() : null)
                .build();
    }
}
