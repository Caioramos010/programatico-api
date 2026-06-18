package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean
    private EmailService emailService;

    private String token;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

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
    void deveExigirAutenticacaoParaCheckout() throws Exception {
        mockMvc.perform(get("/api/payments/checkout-url"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }
}
