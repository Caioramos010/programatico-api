package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentSyncBillIdIntegrationTest {

    private static final HttpServer mockAbacatePay;
    private static final int mockPort;

    static {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v2/checkouts/get", exchange -> {
                String response = "{\"success\":false,\"error\":\"Checkout not found\"}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            mockAbacatePay = server;
            mockPort = server.getAddress().getPort();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void pararMockAbacatePay() {
        mockAbacatePay.stop(0);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean
    private EmailService emailService;

    private String token;

    @DynamicPropertySource
    static void configurarAbacatePayMock(DynamicPropertyRegistry registry) {
        registry.add("abacatepay.api-key", () -> "chave-teste");
        registry.add("abacatepay.product-id", () -> "prod_teste");
        registry.add("abacatepay.api-base-url", () -> "http://localhost:" + mockPort + "/v2");
    }

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("sync-bill-user")
                .email("sync-bill@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build());

        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void syncComBillIdInvalidoDeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/payments/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"bill_inexistente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Checkout not found"));
    }
}
