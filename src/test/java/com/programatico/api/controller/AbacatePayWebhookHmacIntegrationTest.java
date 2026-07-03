package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "abacatepay.webhook.secret=segredo-hmac-teste",
        "abacatepay.webhook.hmac-key=chave-hmac-teste",
        "abacatepay.webhook.require-hmac=true"
})
class AbacatePayWebhookHmacIntegrationTest {

    private static final String HMAC_KEY = "chave-hmac-teste";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        usuario = usuarioRepository.save(Usuario.builder()
                .username("hmac-webhook-user")
                .email("hmac-webhook@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build());
    }

    @Test
    void deveRejeitarWebhookComAssinaturaHmacInvalida() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-hmac-invalido");

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-hmac-teste")
                        .header("X-Webhook-Signature", "assinatura-invalida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertEquals(SubscriptionType.FREE, recarregarUsuario().getSubscriptionType());
    }

    @Test
    void deveRejeitarWebhookSemAssinaturaHmacQuandoExigida() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-hmac-ausente");

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-hmac-teste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertEquals(SubscriptionType.FREE, recarregarUsuario().getSubscriptionType());
    }

    @Test
    void deveAceitarWebhookComAssinaturaHmacValida() throws Exception {
        String body = payloadCheckoutPago(usuario.getId(), "evt-hmac-valido");
        String assinatura = calcularHmac(body, HMAC_KEY);

        mockMvc.perform(post("/api/webhooks/abacatepay")
                        .param("webhookSecret", "segredo-hmac-teste")
                        .header("X-Webhook-Signature", assinatura)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(SubscriptionType.ROOT, recarregarUsuario().getSubscriptionType());
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
                      "id": "chk_hmac_1",
                      "status": "PAID",
                      "externalId": "%d",
                      "amount": 2990,
                      "method": "PIX"
                    }
                  }
                }
                """.formatted(eventId, userId);
    }

    private static String calcularHmac(String body, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
