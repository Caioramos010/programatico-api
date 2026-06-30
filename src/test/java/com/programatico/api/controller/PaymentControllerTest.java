package com.programatico.api.controller;

import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.PaymentDto;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
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
import java.util.List;

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
    void checkoutUrlDeveRetornar400QuandoPagamentoNaoConfigurado() throws Exception {
        when(paymentService.resolveCheckoutUrl("payer"))
                .thenThrow(new BadRequestException(
                        "Pagamento não configurado. Defina ABACATEPAY_API_KEY e ABACATEPAY_PRODUCT_ID, "
                                + "ou ABACATEPAY_CHECKOUT_URL no ambiente."));

        mockMvc.perform(get("/api/payments/checkout-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Pagamento não configurado. Defina ABACATEPAY_API_KEY e ABACATEPAY_PRODUCT_ID, "
                                + "ou ABACATEPAY_CHECKOUT_URL no ambiente."));
    }

    @Test
    @WithMockUser(username = "payer")
    void checkoutUrlDeveRetornar400QuandoUsuarioJaTemRoot() throws Exception {
        when(paymentService.resolveCheckoutUrl("payer"))
                .thenThrow(new BadRequestException("Você já possui o plano Root."));

        mockMvc.perform(get("/api/payments/checkout-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Você já possui o plano Root."));
    }

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
    void syncComBillIdInvalidoDeveRetornar400() throws Exception {
        when(paymentService.sincronizarAssinatura(eq("payer"), eq("bill_invalido")))
                .thenThrow(new BadRequestException("Checkout not found"));

        mockMvc.perform(post("/api/payments/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"bill_invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Checkout not found"));
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

    @Test
    @WithMockUser(username = "root-user")
    void cancelarDeveRetornarPerfilComRenovacaoDesativada() throws Exception {
        UsuarioDto.Response response = UsuarioDto.Response.builder()
                .id(1L)
                .username("root-user")
                .email("root@test.com")
                .subscriptionType(SubscriptionType.ROOT)
                .subscriptionExpiresAt(Instant.parse("2026-07-01T00:00:00Z"))
                .subscriptionAutoRenew(false)
                .role(TipoUsuario.USER)
                .build();

        when(paymentService.cancelarAssinatura("root-user")).thenReturn(response);

        mockMvc.perform(post("/api/payments/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionType").value("ROOT"))
                .andExpect(jsonPath("$.subscriptionAutoRenew").value(false));
    }

    @Test
    @WithMockUser(username = "payer")
    void historicoDeveRetornarListaDePagamentos() throws Exception {
        PaymentDto.Response item = PaymentDto.Response.builder()
                .id(1L)
                .amount(new java.math.BigDecimal("29.90"))
                .status(com.programatico.api.domain.enums.PaymentStatus.PAID)
                .method(com.programatico.api.domain.enums.PaymentMethod.PIX)
                .billId("chk_1")
                .createdAt(Instant.parse("2025-06-01T12:00:00Z"))
                .build();

        when(paymentService.listarHistorico("payer")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/payments/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].method").value("PIX"))
                .andExpect(jsonPath("$[0].amount").value(29.90));
    }
}
