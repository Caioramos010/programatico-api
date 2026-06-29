package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AbacatePayWebhookService abacatePayWebhookService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void configurarPropriedades() {
        ReflectionTestUtils.setField(paymentService, "apiKey", "");
        ReflectionTestUtils.setField(paymentService, "productId", "");
        ReflectionTestUtils.setField(paymentService, "staticCheckoutUrl", "https://checkout.exemplo.com/pagar");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(paymentService, "apiBaseUrl", "https://api.abacatepay.com/v2");
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
}
