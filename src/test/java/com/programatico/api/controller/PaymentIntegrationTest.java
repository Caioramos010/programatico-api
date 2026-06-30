package com.programatico.api.controller;

import com.programatico.api.domain.Payment;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.TwoFactorBackupCodeRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
import com.programatico.api.security.JwtUtil;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "abacatepay.api-key=",
        "abacatepay.product-id=",
        "abacatepay.checkout-url=https://checkout.integracao.exemplo/pagar"
})
class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private TwoFactorBackupCodeRepository backupCodeRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean
    private EmailService emailService;

    private String token;

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(
                usuarioRepository, userSettingsRepository, paymentRepository, backupCodeRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("payment-user")
                .email("payment@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void deveRetornarUrlEstaticaDeCheckout() throws Exception {
        mockMvc.perform(get("/api/payments/checkout-url")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.integracao.exemplo/pagar"))
                .andExpect(jsonPath("$.billId").value(""));
    }

    @Test
    void deveBloquearCheckoutParaUsuarioComRootAtivo() throws Exception {
        Usuario usuario = usuarioRepository.findByUsername("payment-user").orElseThrow();
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS));
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/api/payments/checkout-url")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Você já possui o plano Root."));
    }

    @Test
    void syncDeveRetornarPerfilSemApiKeyConfigurada() throws Exception {
        mockMvc.perform(post("/api/payments/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("payment-user"))
                .andExpect(jsonPath("$.subscriptionType").value("FREE"));
    }

    @Test
    void rootExpiradoDevePermitirCheckout() throws Exception {
        Usuario usuario = usuarioRepository.findByUsername("payment-user").orElseThrow();
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/api/payments/checkout-url")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.integracao.exemplo/pagar"));
    }

    @Test
    void cancelarAssinaturaDeveDesativarRenovacaoAutomatica() throws Exception {
        Usuario usuario = usuarioRepository.findByUsername("payment-user").orElseThrow();
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
        usuario.setSubscriptionAutoRenew(true);
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/api/payments/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionType").value("ROOT"))
                .andExpect(jsonPath("$.subscriptionAutoRenew").value(false));
    }

    @Test
    void cancelarSemRootAtivoDeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/payments/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Você não possui uma assinatura Root ativa para cancelar."));
    }

    @Test
    void historicoDeveRetornarListaVaziaQuandoSemPagamentos() throws Exception {
        mockMvc.perform(get("/api/payments/historico")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historicoDeveRetornarPagamentosDoUsuario() throws Exception {
        Usuario usuario = usuarioRepository.findByUsername("payment-user").orElseThrow();
        paymentRepository.save(Payment.builder()
                .usuario(usuario)
                .amount(new BigDecimal("29.90"))
                .status(PaymentStatus.PAID)
                .method(PaymentMethod.PIX)
                .billId("chk_integracao")
                .createdAt(Instant.parse("2025-06-01T12:00:00Z"))
                .build());

        mockMvc.perform(get("/api/payments/historico")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].method").value("PIX"))
                .andExpect(jsonPath("$[0].billId").value("chk_integracao"));
    }

    @Test
    void deveExigirAutenticacaoParaSyncEHistorico() throws Exception {
        mockMvc.perform(post("/api/payments/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });

        mockMvc.perform(get("/api/payments/historico"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void deveExigirAutenticacaoParaCheckout() throws Exception {
        mockMvc.perform(get("/api/payments/checkout-url"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }
}
