package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.PaymentDto;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AbacatePayWebhookService abacatePayWebhookService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    private HttpServer mockAbacatePay;

    @BeforeEach
    void configurarPropriedades() {
        ReflectionTestUtils.setField(paymentService, "apiKey", "");
        ReflectionTestUtils.setField(paymentService, "productId", "");
        ReflectionTestUtils.setField(paymentService, "staticCheckoutUrl", "https://checkout.exemplo.com/pagar");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(paymentService, "apiBaseUrl", "https://api.abacatepay.com/v2");
    }

    @AfterEach
    void pararMockAbacatePay() {
        if (mockAbacatePay != null) {
            mockAbacatePay.stop(0);
            mockAbacatePay = null;
        }
    }

    @Test
    void resolveCheckoutUrlComApiConfiguradaDeveCriarCheckout() throws Exception {
        iniciarMockAbacatePay("""
                {
                  "success": true,
                  "data": {
                    "id": "chk_novo",
                    "url": "https://pay.abacatepay.com/checkout/chk_novo"
                  }
                }
                """, "/v2/checkouts/create");

        ReflectionTestUtils.setField(paymentService, "apiKey", "api-key-teste");
        ReflectionTestUtils.setField(paymentService, "productId", "prod_teste");
        ReflectionTestUtils.setField(paymentService, "staticCheckoutUrl", "");
        ReflectionTestUtils.setField(paymentService, "apiBaseUrl",
                "http://localhost:" + mockAbacatePay.getAddress().getPort() + "/v2");

        Usuario usuario = usuarioBase(10L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));

        PaymentService.CheckoutResult result = paymentService.resolveCheckoutUrl("payer");

        assertEquals("https://pay.abacatepay.com/checkout/chk_novo", result.url());
        assertEquals("chk_novo", result.billId());
    }

    @Test
    void sincronizarAssinaturaComBillPagoDeveAtivarRoot() throws Exception {
        iniciarMockAbacatePay("""
                {
                  "success": true,
                  "data": {
                    "id": "chk_pago",
                    "status": "PAID",
                    "externalId": "11"
                  }
                }
                """, "/v2/checkouts/get");

        ReflectionTestUtils.setField(paymentService, "apiKey", "api-key-teste");
        ReflectionTestUtils.setField(paymentService, "apiBaseUrl",
                "http://localhost:" + mockAbacatePay.getAddress().getPort() + "/v2");

        Usuario usuario = usuarioBase(11L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findById(11L)).thenReturn(Optional.of(usuario));

        paymentService.sincronizarAssinatura("payer", "chk_pago");

        verify(abacatePayWebhookService).ativarPlanoRoot(11L);
    }

    @Test
    void sincronizarAssinaturaComBillInvalidoDeveLancarBadRequest() throws Exception {
        iniciarMockAbacatePay("""
                {
                  "success": true,
                  "data": {
                    "id": "chk_pendente",
                    "status": "PENDING",
                    "externalId": "12"
                  }
                }
                """, "/v2/checkouts/get");

        ReflectionTestUtils.setField(paymentService, "apiKey", "api-key-teste");
        ReflectionTestUtils.setField(paymentService, "apiBaseUrl",
                "http://localhost:" + mockAbacatePay.getAddress().getPort() + "/v2");

        Usuario usuario = usuarioBase(12L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> paymentService.sincronizarAssinatura("payer", "chk_pendente"));

        assertEquals("Pagamento não encontrado ou ainda não confirmado para o billId informado.", ex.getMessage());
        verify(abacatePayWebhookService, never()).ativarPlanoRoot(anyLong());
    }

    @Test
    void resolveCheckoutUrlDeveRetornarUrlEstaticaQuandoApiNaoConfigurada() {
        Usuario usuario = usuarioBase(1L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));

        PaymentService.CheckoutResult result = paymentService.resolveCheckoutUrl("payer");

        assertEquals("https://checkout.exemplo.com/pagar", result.url());
        assertEquals("", result.billId());
    }

    @Test
    void resolveCheckoutUrlDeveLancarQuandoPagamentoNaoConfigurado() {
        ReflectionTestUtils.setField(paymentService, "staticCheckoutUrl", "");
        Usuario usuario = usuarioBase(1L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> paymentService.resolveCheckoutUrl("payer"));

        assertEquals(
                "Pagamento não configurado. Defina ABACATEPAY_API_KEY e ABACATEPAY_PRODUCT_ID, "
                        + "ou ABACATEPAY_CHECKOUT_URL no ambiente.",
                ex.getMessage()
        );
    }

    @Test
    void resolveCheckoutUrlDeveBloquearUsuarioComRootAtivo() {
        Usuario usuario = usuarioBase(2L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        when(usuarioRepository.findByUsername("root-user")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> paymentService.resolveCheckoutUrl("root-user"));

        assertEquals("Você já possui o plano Root.", ex.getMessage());
    }

    @Test
    void resolveCheckoutUrlDevePermitirRootExpirado() {
        Usuario usuario = usuarioBase(3L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(usuarioRepository.findByUsername("root-expirado")).thenReturn(Optional.of(usuario));

        PaymentService.CheckoutResult result = paymentService.resolveCheckoutUrl("root-expirado");

        assertEquals("https://checkout.exemplo.com/pagar", result.url());
    }

    @Test
    void resolveCheckoutUrlDeveLancarQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.resolveCheckoutUrl("inexistente"));
    }

    @Test
    void sincronizarAssinaturaSemApiKeyDeveRetornarUsuarioSemAlteracao() {
        Usuario usuario = usuarioBase(5L);
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));

        UsuarioDto.Response response = paymentService.sincronizarAssinatura("payer", null);

        assertEquals(SubscriptionType.FREE, response.getSubscriptionType());
        verify(abacatePayWebhookService, never()).ativarPlanoRoot(5L);
    }

    @Test
    void sincronizarAssinaturaComRootAtivoNaoDeveSincronizar() {
        ReflectionTestUtils.setField(paymentService, "apiKey", "api-key-teste");
        Usuario usuario = usuarioBase(6L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        when(usuarioRepository.findByUsername("root-user")).thenReturn(Optional.of(usuario));

        UsuarioDto.Response response = paymentService.sincronizarAssinatura("root-user", "bill-123");

        assertEquals(SubscriptionType.ROOT, response.getSubscriptionType());
        verify(abacatePayWebhookService, never()).ativarPlanoRoot(6L);
    }

    @Test
    void sincronizarAssinaturaDeveLancarQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.sincronizarAssinatura("ghost", null));
    }

    @Test
    void cancelarAssinaturaDeveDesativarRenovacaoAutomatica() {
        Usuario usuario = usuarioBase(7L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(true);
        when(usuarioRepository.findByUsername("root-user")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioDto.Response response = paymentService.cancelarAssinatura("root-user");

        assertEquals(false, response.getSubscriptionAutoRenew());
        assertEquals(SubscriptionType.ROOT, response.getSubscriptionType());
        assertEquals(false, usuario.getSubscriptionAutoRenew());
    }

    @Test
    void cancelarAssinaturaDeveLancarQuandoNaoHaRootAtivo() {
        Usuario usuario = usuarioBase(8L);
        when(usuarioRepository.findByUsername("free-user")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> paymentService.cancelarAssinatura("free-user"));

        assertEquals("Você não possui uma assinatura Root ativa para cancelar.", ex.getMessage());
    }

    @Test
    void cancelarAssinaturaDeveLancarQuandoRenovacaoJaCancelada() {
        Usuario usuario = usuarioBase(9L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(false);
        when(usuarioRepository.findByUsername("root-user")).thenReturn(Optional.of(usuario));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> paymentService.cancelarAssinatura("root-user"));

        assertEquals("A renovação automática já está cancelada.", ex.getMessage());
    }

    @Test
    void listarHistoricoDeveRetornarPagamentosDoUsuario() {
        Usuario usuario = usuarioBase(20L);
        com.programatico.api.domain.Payment payment = com.programatico.api.domain.Payment.builder()
                .id(1L)
                .usuario(usuario)
                .amount(new BigDecimal("29.90"))
                .status(PaymentStatus.PAID)
                .method(PaymentMethod.PIX)
                .billId("chk_1")
                .createdAt(Instant.parse("2025-06-01T12:00:00Z"))
                .build();
        when(usuarioRepository.findByUsername("payer")).thenReturn(Optional.of(usuario));
        when(paymentRepository.findByUsuarioOrderByCreatedAtDesc(usuario)).thenReturn(List.of(payment));

        List<PaymentDto.Response> historico = paymentService.listarHistorico("payer");

        assertEquals(1, historico.size());
        assertEquals(PaymentStatus.PAID, historico.get(0).getStatus());
        assertEquals(0, historico.get(0).getAmount().compareTo(new BigDecimal("29.90")));
    }

    private static Usuario usuarioBase(Long id) {
        return Usuario.builder()
                .id(id)
                .username("user-" + id)
                .email("user" + id + "@test.com")
                .senha("hash")
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build();
    }

    private void iniciarMockAbacatePay(String jsonResponse, String path) throws IOException {
        mockAbacatePay = HttpServer.create(new InetSocketAddress(0), 0);
        mockAbacatePay.createContext(path, exchange -> {
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        mockAbacatePay.start();
    }
}
