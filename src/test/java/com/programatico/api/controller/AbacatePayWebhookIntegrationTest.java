package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.ProcessedAbacateWebhookRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "abacatepay.webhook.secret=segredo-integracao",
        "abacatepay.webhook.hmac-key=",
        "abacatepay.webhook.require-hmac=false",
        "abacatepay.root-duration-days=30"
})
class AbacatePayWebhookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProcessedAbacateWebhookRepository processedRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        processedRepository.deleteAll();
        paymentRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(Usuario.builder()
                .username("webhook-user")
                .email("webhook@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build());
    }

    @Test
    void deveRejeitarWebhookComSegredoInvalido() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-unauth");

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertEquals(SubscriptionType.FREE, recarregarUsuario().getSubscriptionType());
    }

    @Test
    void deveAtivarPlanoRootQuandoCheckoutPago() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-pago-1");

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-integracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Usuario atualizado = recarregarUsuario();
        assertEquals(SubscriptionType.ROOT, atualizado.getSubscriptionType());
        assertEquals(1, processedRepository.count());
        assertEquals(1, paymentRepository.count());
        assertEquals("chk_pago_1", paymentRepository.findAll().get(0).getBillId());
    }

    @Test
    void deveSerIdempotenteParaMesmoEvento() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-idem");

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-integracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-integracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(1, processedRepository.count());
        assertEquals(SubscriptionType.ROOT, recarregarUsuario().getSubscriptionType());
    }

    @Test
    void deveAceitarEventoNaoPagoSemAtivarPlano() throws Exception {
        String body = """
                {
                  "id": "evt-pending",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "status": "PENDING",
                      "externalId": "%d"
                    }
                  }
                }
                """.formatted(usuario.getId());

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-integracao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(SubscriptionType.FREE, recarregarUsuario().getSubscriptionType());
        assertEquals(1, processedRepository.count());
    }

    private Usuario recarregarUsuario() {
        return usuarioRepository.findById(usuario.getId()).orElseThrow();
    }

    private static String payloadCheckoutPago(Long userId, String eventId) {
        return """
                {
                  "id": "%s",
                  "event": "checkout.completed",
                  "data": {
                    "checkout": {
                      "id": "chk_pago_1",
                      "status": "PAID",
                      "externalId": "%d",
                      "amount": 2990,
                      "method": "PIX"
                    }
                  }
                }
                """.formatted(eventId, userId);
    }
}
