package com.programatico.api.controller;

import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.AbacatePayWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AbacatePayWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class AbacatePayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AbacatePayWebhookService abacatePayWebhookService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void deveRetornar401QuandoWebhookSecretInvalido() throws Exception {
        when(abacatePayWebhookService.secretValido("segredo-errado")).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(abacatePayWebhookService, never()).processarSeNecessario(anyString());
    }

    @Test
    void deveRetornar403QuandoAssinaturaHmacInvalida() throws Exception {
        String body = "{\"event\":\"checkout.completed\"}";
        when(abacatePayWebhookService.secretValido("segredo-ok")).thenReturn(true);
        when(abacatePayWebhookService.isRequireHmac()).thenReturn(true);
        when(abacatePayWebhookService.assinaturaValida(eq(body), eq("hmac-errado"))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-ok")
                        .header("X-Webhook-Signature", "hmac-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        verify(abacatePayWebhookService, never()).processarSeNecessario(anyString());
    }

    @Test
    void deveRetornar403QuandoAssinaturaHmacAusente() throws Exception {
        String body = "{\"event\":\"checkout.completed\"}";
        when(abacatePayWebhookService.secretValido("segredo-ok")).thenReturn(true);
        when(abacatePayWebhookService.isRequireHmac()).thenReturn(true);
        when(abacatePayWebhookService.assinaturaValida(eq(body), eq(null))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        verify(abacatePayWebhookService, never()).processarSeNecessario(anyString());
    }
}
