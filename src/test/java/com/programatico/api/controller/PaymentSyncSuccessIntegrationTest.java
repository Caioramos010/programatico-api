package com.programatico.api.controller;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import com.programatico.api.testsupport.IntegrationTestDbCleaner;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentSyncSuccessIntegrationTest {

    private static final HttpServer mockAbacatePay;
    private static final int mockPort;
    private static volatile String checkoutResponseJson = "{}";

    static {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v2/checkouts/get", exchange -> {
                byte[] bytes = checkoutResponseJson.getBytes(StandardCharsets.UTF_8);
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
    static void pararMock() {
        mockAbacatePay.stop(0);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private EmailService emailService;

    private String token;
    private Long userId;

    @DynamicPropertySource
    static void configurarAbacatePay(DynamicPropertyRegistry registry) {
        registry.add("abacatepay.api-key", () -> "chave-teste");
        registry.add("abacatepay.product-id", () -> "prod_teste");
        registry.add("abacatepay.api-base-url", () -> "http://localhost:" + mockPort + "/v2");
        registry.add("abacatepay.root-duration-days", () -> "30");
    }

    @BeforeEach
    void setUp() {
        IntegrationTestDbCleaner.limparUsuarios(usuarioRepository, userSettingsRepository);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("sync-ok-user")
                .email("sync-ok@test.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .subscriptionType(SubscriptionType.FREE)
                .build());

        userId = usuario.getId();
        token = jwtUtil.gerarToken(usuario.getUsername(), usuario.getId());
    }

    @Test
    void syncComBillPagoDeveAtivarRoot() throws Exception {
        checkoutResponseJson = """
                {
                  "success": true,
                  "data": {
                    "id": "chk_pago_integracao",
                    "status": "PAID",
                    "externalId": "%d"
                  }
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/payments/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"chk_pago_integracao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionType").value("ROOT"));

        Usuario atualizado = usuarioRepository.findById(userId).orElseThrow();
        assertEquals(SubscriptionType.ROOT, atualizado.getSubscriptionType());
    }
}
