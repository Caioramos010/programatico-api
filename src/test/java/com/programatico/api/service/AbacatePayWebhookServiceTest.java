package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Payment;
import com.programatico.api.domain.ProcessedAbacateWebhook;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.ProcessedAbacateWebhookRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbacatePayWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "segredo-webhook-teste";
    private static final String HMAC_KEY = "chave-hmac-teste";

    @Mock private ProcessedAbacateWebhookRepository processedRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PaymentRepository paymentRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AbacatePayWebhookService webhookService;

    @BeforeEach
    void configurarPropriedades() {
        ReflectionTestUtils.setField(webhookService, "webhookSecretConfig", WEBHOOK_SECRET);
        ReflectionTestUtils.setField(webhookService, "hmacKey", HMAC_KEY);
        ReflectionTestUtils.setField(webhookService, "requireHmac", true);
        ReflectionTestUtils.setField(webhookService, "rootDurationDays", 30);
        lenient().when(paymentRepository.findByBillId(anyString())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void secretValidoDeveAceitarSegredoCorreto() {
        assertTrue(webhookService.secretValido(WEBHOOK_SECRET));
    }

    @Test
    void secretValidoDeveRejeitarSegredoIncorreto() {
        assertFalse(webhookService.secretValido("outro-segredo"));
    }

    @Test
    void secretValidoDeveRejeitarQuandoNaoConfigurado() {
        ReflectionTestUtils.setField(webhookService, "webhookSecretConfig", "");
        assertFalse(webhookService.secretValido(WEBHOOK_SECRET));
    }

    @Test
    void assinaturaValidaDeveAceitarHmacCorreto() throws Exception {
        String body = "{\"event\":\"checkout.completed\"}";
        String assinatura = calcularHmac(body, HMAC_KEY);
        assertTrue(webhookService.assinaturaValida(body, assinatura));
    }

    @Test
    void assinaturaValidaDeveRejeitarHmacIncorreto() {
        assertFalse(webhookService.assinaturaValida("{}", "assinatura-invalida"));
    }

    @Test
    void assinaturaValidaDeveRejeitarQuandoHeaderAusente() {
        assertFalse(webhookService.assinaturaValida("{}", null));
    }

    @Test
    void assinaturaValidaDeveRejeitarQuandoChaveHmacNaoConfigurada() {
        ReflectionTestUtils.setField(webhookService, "hmacKey", "");
        assertFalse(webhookService.assinaturaValida("{}", "qualquer"));
    }

    @Test
    void processarCheckoutCompletoPagoDeveAtivarPlanoRoot() throws Exception {
        Usuario usuario = usuarioFree(42L);
        when(processedRepository.existsById("evt-1")).thenReturn(false);
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-1",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_42",
                      "status": "PAID",
                      "externalId": "42",
                      "amount": 2990,
                      "method": "PIX"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
        assertTrue(Boolean.TRUE.equals(usuario.getSubscriptionAutoRenew()));
        assertNotNull(usuario.getSubscriptionExpiresAt());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.PAID, paymentCaptor.getValue().getStatus());
        assertEquals(PaymentMethod.PIX, paymentCaptor.getValue().getMethod());
        assertEquals(0, paymentCaptor.getValue().getAmount().compareTo(new java.math.BigDecimal("29.90")));
        verify(processedRepository).save(any(ProcessedAbacateWebhook.class));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void assinaturaValidaDeveRejeitarHmacComCorpoAlterado() throws Exception {
        String body = "{\"event\":\"checkout.completed\"}";
        String assinatura = calcularHmac(body, HMAC_KEY);
        assertFalse(webhookService.assinaturaValida(body + " ", assinatura));
    }

    @Test
    void processarEventoDuplicadoNaoDeveAlterarPagamento() throws Exception {
        when(processedRepository.existsById("evt-dup")).thenReturn(true);

        String body = """
                {
                  "id": "evt-dup",
                  "event": "checkout.completed",
                  "data": { "checkout": { "status": "PAID", "externalId": "1" } }
                }
                """;

        webhookService.processarSeNecessario(body);

        verify(usuarioRepository, never()).findById(any());
        verify(processedRepository, never()).save(any());
    }

    @Test
    void processarEventoNaoPagoDeveRegistrarPendenteSemAtivar() throws Exception {
        Usuario usuario = usuarioFree(7L);
        when(processedRepository.existsById("evt-pending")).thenReturn(false);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        String body = """
                {
                  "id": "evt-pending",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_pending",
                      "status": "PENDING",
                      "externalId": "7",
                      "amount": 2990
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        verify(usuarioRepository, never()).save(any());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.PENDING, captor.getValue().getStatus());
        verify(processedRepository).save(any(ProcessedAbacateWebhook.class));
    }

    @Test
    void processarPagoSemUserIdDeveIgnorarAtivacao() throws Exception {
        when(processedRepository.existsById("evt-sem-user")).thenReturn(false);

        String body = """
                {
                  "id": "evt-sem-user",
                  "event": "checkout.completed",
                  "data": { "checkout": { "status": "PAID", "externalId": "nao-numerico" } }
                }
                """;

        webhookService.processarSeNecessario(body);

        verify(usuarioRepository, never()).save(any());
        verify(processedRepository).save(any(ProcessedAbacateWebhook.class));
    }

    @Test
    void processarDeveExtrairUserIdDeMetadata() throws Exception {
        Usuario usuario = usuarioFree(99L);
        when(processedRepository.existsById("evt-meta")).thenReturn(false);
        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-meta",
                  "event": "checkout.completed",
                  "metadata": { "userId": "99" },
                  "data": { "checkout": { "status": "PAID" } }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
    }

    @Test
    void processarDeveAceitarConvençãoUserUnderscore() throws Exception {
        Usuario usuario = usuarioFree(15L);
        when(processedRepository.existsById("evt-conv")).thenReturn(false);
        when(usuarioRepository.findById(15L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-conv",
                  "event": "transparent.completed",
                  "data": {
                    "transparent": {
                      "status": "PAID",
                      "externalId": "user_15"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
    }

    @Test
    void processarSubscriptionRenewedDeveAtivarQuandoPagamentoPago() throws Exception {
        Usuario usuario = usuarioFree(3L);
        when(processedRepository.existsById("evt-sub")).thenReturn(false);
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-sub",
                  "event": "subscription.renewed",
                  "data": {
                    "payment": { "status": "PAID", "externalId": "3" }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
        assertTrue(Boolean.TRUE.equals(usuario.getSubscriptionAutoRenew()));
    }

    @Test
    void processarSubscriptionRenewedDeveIgnorarQuandoRenovacaoCancelada() throws Exception {
        Usuario usuario = usuarioFree(3L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(false);
        Instant expiracaoOriginal = usuario.getSubscriptionExpiresAt();
        when(processedRepository.existsById("evt-sub-cancel")).thenReturn(false);
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(paymentRepository.findByBillId("pay_3")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-sub-cancel",
                  "event": "subscription.renewed",
                  "data": {
                    "payment": {
                      "id": "pay_3",
                      "status": "PAID",
                      "externalId": "3",
                      "amount": 1990,
                      "method": "CARD"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(expiracaoOriginal, usuario.getSubscriptionExpiresAt());
        verify(usuarioRepository, never()).save(any());
        verify(paymentRepository).save(any(Payment.class));
        verify(processedRepository).save(any(ProcessedAbacateWebhook.class));
    }

    @Test
    void processarCheckoutCompletoDeveReativarMesmoComRenovacaoCancelada() throws Exception {
        Usuario usuario = usuarioFree(4L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(false);
        when(processedRepository.existsById("evt-rebuy")).thenReturn(false);
        when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-rebuy",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "status": "PAID",
                      "externalId": "4"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
        assertTrue(Boolean.TRUE.equals(usuario.getSubscriptionAutoRenew()));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void processarSemIdDeveUsarEventIdSintetico() throws Exception {
        Usuario usuario = usuarioFree(8L);
        String eventIdEsperado = "synthetic:checkout.completed:chk_abc:2025-01-01T00:00:00Z";
        when(processedRepository.existsById(eventIdEsperado)).thenReturn(false);
        when(usuarioRepository.findById(8L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_abc",
                      "status": "PAID",
                      "externalId": "8",
                      "updatedAt": "2025-01-01T00:00:00Z"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        ArgumentCaptor<ProcessedAbacateWebhook> captor = ArgumentCaptor.forClass(ProcessedAbacateWebhook.class);
        verify(processedRepository).save(captor.capture());
        assertEquals(eventIdEsperado, captor.getValue().getEventId());
        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
        assertTrue(Boolean.TRUE.equals(usuario.getSubscriptionAutoRenew()));
    }

    @Test
    void ativarPlanoRootDeveDefinirExpiracaoConformeConfiguracao() {
        Usuario usuario = usuarioFree(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant antes = Instant.now();
        webhookService.ativarPlanoRoot(1L);
        Instant depois = Instant.now().plus(30, ChronoUnit.DAYS);

        assertEquals(SubscriptionType.ROOT, usuario.getSubscriptionType());
        assertTrue(usuario.getSubscriptionExpiresAt().isAfter(antes.plus(29, ChronoUnit.DAYS)));
        assertTrue(usuario.getSubscriptionExpiresAt().isBefore(depois.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    void processarCheckoutExpiradoDeveRegistrarFalhaSemAtivarRoot() throws Exception {
        Usuario usuario = usuarioFree(20L);
        when(processedRepository.existsById("evt-expired")).thenReturn(false);
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(usuario));

        String body = """
                {
                  "id": "evt-expired",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_exp",
                      "status": "EXPIRED",
                      "externalId": "20",
                      "amount": 2990
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(SubscriptionType.FREE, usuario.getSubscriptionType());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void processarCheckoutFalhoDeveRegistrarFalhaSemAtivarRoot() throws Exception {
        Usuario usuario = usuarioFree(21L);
        when(processedRepository.existsById("evt-failed")).thenReturn(false);
        when(usuarioRepository.findById(21L)).thenReturn(Optional.of(usuario));

        String body = """
                {
                  "id": "evt-failed",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_fail",
                      "status": "FAILED",
                      "externalId": "21"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        verify(usuarioRepository, never()).save(any());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void processarReembolsoDeveAtualizarPagamentoEEncerrarRoot() throws Exception {
        Usuario usuario = usuarioFree(22L);
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(true);

        Payment pago = Payment.builder()
                .id(1L)
                .usuario(usuario)
                .amount(new java.math.BigDecimal("29.90"))
                .status(PaymentStatus.PAID)
                .method(PaymentMethod.PIX)
                .billId("chk_refund")
                .build();

        when(processedRepository.existsById("evt-refund")).thenReturn(false);
        when(usuarioRepository.findById(22L)).thenReturn(Optional.of(usuario));
        when(paymentRepository.findByBillId("chk_refund")).thenReturn(Optional.of(pago));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                {
                  "id": "evt-refund",
                  "event": "checkout.refunded",
                  "data": {
                    "checkout": {
                      "id": "chk_refund",
                      "status": "REFUNDED",
                      "externalId": "22",
                      "amount": 2990
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        assertEquals(PaymentStatus.REFUNDED, pago.getStatus());
        assertEquals(SubscriptionType.FREE, usuario.getSubscriptionType());
        assertEquals(false, usuario.getSubscriptionAutoRenew());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void processarEventoExpiradoPorNomeDeveRegistrarFalha() throws Exception {
        Usuario usuario = usuarioFree(23L);
        when(processedRepository.existsById("evt-exp-name")).thenReturn(false);
        when(usuarioRepository.findById(23L)).thenReturn(Optional.of(usuario));

        String body = """
                {
                  "id": "evt-exp-name",
                  "event": "checkout.expired",
                  "data": {
                    "checkout": {
                      "id": "chk_exp2",
                      "externalId": "23"
                    }
                  }
                }
                """;

        webhookService.processarSeNecessario(body);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void ativarPlanoRootDeveIgnorarUsuarioInexistente() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        webhookService.ativarPlanoRoot(999L);

        verify(usuarioRepository, never()).save(any());
    }

    private static Usuario usuarioFree(Long id) {
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

    private static String calcularHmac(String body, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
