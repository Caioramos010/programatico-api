package com.programatico.api.controller;

import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.security.JwtAuthFilter;
import com.programatico.api.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @WithMockUser(username = "payer")
    void checkoutUrlDeveRetornarUrlEBillId() throws Exception {
        when(paymentService.resolveCheckoutUrl("payer"))
                .thenReturn(new PaymentService.CheckoutResult("https://pay.exemplo.com", "bill_abc"));

        mockMvc.perform(get("/api/payments/checkout-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://pay.exemplo.com"))
                .andExpect(jsonPath("$.billId").value("bill_abc"));
    }

    @Test
    @WithMockUser(username = "payer")
    void syncDeveRetornarPerfilAtualizado() throws Exception {
        UsuarioDto.Response response = UsuarioDto.Response.builder()
                .id(1L)
                .username("payer")
                .email("payer@test.com")
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(Instant.parse("2026-07-01T00:00:00Z"))
                .role(TipoUsuario.USER)
                .build();

        when(paymentService.sincronizarAssinatura(eq("payer"), eq("bill_xyz"))).thenReturn(response);

        mockMvc.perform(post("/api/payments/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"bill_xyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionType").value("ROOT"))
                .andExpect(jsonPath("$.username").value("payer"));
    }

    @Test
    @WithMockUser(username = "payer")
    void syncSemBodyDeveChamarServicoComBillIdNulo() throws Exception {
        UsuarioDto.Response response = UsuarioDto.Response.builder()
                .id(1L)
                .username("payer")
                .email("payer@test.com")
                .subscriptionType(SubscriptionType.FREE)
                .role(TipoUsuario.USER)
                .build();

        when(paymentService.sincronizarAssinatura(eq("payer"), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/payments/sync")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionType").value("FREE"));
    }
}
