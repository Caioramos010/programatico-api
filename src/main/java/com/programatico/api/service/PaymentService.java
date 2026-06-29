package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.exception.BadRequestException;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    public record CheckoutResult(String url, String billId) {}

    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final AbacatePayWebhookService abacatePayWebhookService;

    @Value("${abacatepay.api-key:}")
    private String apiKey;

    @Value("${abacatepay.api-base-url:https://api.abacatepay.com/v2}")
    private String apiBaseUrl;

    @Value("${abacatepay.product-id:}")
    private String productId;

    @Value("${abacatepay.checkout-url:}")
    private String staticCheckoutUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public CheckoutResult resolveCheckoutUrl(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (usuario.getSubscriptionType() == SubscriptionType.ROOT && assinaturaRootAtiva(usuario)) {
            throw new BadRequestException("Você já possui o plano Root.");
        }

        if (StringUtils.hasText(apiKey) && StringUtils.hasText(productId)) {
            return criarCheckoutAbacatePay(usuario);
        }

        String url = staticCheckoutUrl == null ? "" : staticCheckoutUrl.trim();
        if (url.isEmpty()) {
            throw new BadRequestException(
                    "Pagamento não configurado. Defina ABACATEPAY_API_KEY e ABACATEPAY_PRODUCT_ID, "
                            + "ou ABACATEPAY_CHECKOUT_URL no ambiente."
            );
        }
        return new CheckoutResult(url, "");
    }

    public UsuarioDto.Response sincronizarAssinatura(String username, String billId) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (!StringUtils.hasText(apiKey)) {
            return UsuarioDto.Response.fromEntity(usuario);
        }

        if (usuario.getSubscriptionType() != SubscriptionType.ROOT || !assinaturaRootAtiva(usuario)) {
            if (StringUtils.hasText(billId)) {
                tentarAtivarPorBill(usuario, billId.trim());
            } else {
                tentarAtivarPorCheckoutsPagos(usuario);
            }
            usuario = usuarioRepository.findById(usuario.getId()).orElseThrow();
        }

        return UsuarioDto.Response.fromEntity(usuario);
    }

    public UsuarioDto.Response cancelarAssinatura(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (usuario.getSubscriptionType() != SubscriptionType.ROOT || !assinaturaRootAtiva(usuario)) {
            throw new BadRequestException("Você não possui uma assinatura Root ativa para cancelar.");
        }
        if (Boolean.FALSE.equals(usuario.getSubscriptionAutoRenew())) {
            throw new BadRequestException("A renovação automática já está cancelada.");
        }

        usuario.setSubscriptionAutoRenew(false);
        usuarioRepository.save(usuario);
        log.info("Renovação automática cancelada: userId={}, expiraEm={}", usuario.getId(), usuario.getSubscriptionExpiresAt());

        return UsuarioDto.Response.fromEntity(usuario);
    }

    private void tentarAtivarPorCheckoutsPagos(Usuario usuario) {
        String userId = String.valueOf(usuario.getId());
        try {
            String uri = UriComponentsBuilder.fromPath("/checkouts/list")
                    .queryParam("externalId", userId)
                    .queryParam("limit", 10)
                    .build()
                    .toUriString();
            JsonNode data = abacatePayGet(uri).path("data");
            if (!data.isArray()) {
                return;
            }
            for (JsonNode checkout : data) {
                if (checkoutPagoDoUsuario(checkout, userId)) {
                    abacatePayWebhookService.ativarPlanoRoot(usuario.getId());
                    log.info("Plano ROOT ativado via sync AbacatePay para usuário id={}", userId);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao sincronizar checkouts pagos para userId={}: {}", usuario.getId(), e.getMessage());
        }
    }

    private void tentarAtivarPorBill(Usuario usuario, String billId) {
        String userId = String.valueOf(usuario.getId());
        try {
            JsonNode checkout = abacatePayGet("/checkouts/get?id=" + billId).path("data");
            if (checkoutPagoDoUsuario(checkout, userId)) {
                abacatePayWebhookService.ativarPlanoRoot(usuario.getId());
                log.info("Plano ROOT ativado via sync bill {} para usuário id={}", billId, userId);
            }
        } catch (Exception e) {
            log.warn("Falha ao sincronizar bill {} para userId={}: {}", billId, usuario.getId(), e.getMessage());
        }
    }

    private boolean checkoutPagoDoUsuario(JsonNode checkout, String userId) {
        if (checkout == null || checkout.isMissingNode()) {
            return false;
        }
        if (!"PAID".equalsIgnoreCase(checkout.path("status").asText(""))) {
            return false;
        }
        String externalId = checkout.path("externalId").asText("");
        if (userId.equals(externalId.trim())) {
            return true;
        }
        String metaUserId = checkout.path("metadata").path("userId").asText("");
        return userId.equals(metaUserId.trim());
    }

    private CheckoutResult criarCheckoutAbacatePay(Usuario usuario) {
        String userId = String.valueOf(usuario.getId());
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("id", productId.trim(), "quantity", 1)));
        body.put("externalId", userId);
        body.put("metadata", Map.of("userId", userId));
        body.put("returnUrl", base + "/seja-root");
        body.put("completionUrl", base + "/aprender?root=sync");
        body.put("methods", List.of("PIX", "CARD"));

        try {
            String responseBody = abacatePayClient().post()
                    .uri("/checkouts/create")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.path("success").asBoolean(false)) {
                String error = root.path("error").asText("Erro desconhecido da AbacatePay");
                log.warn("AbacatePay recusou checkout para userId={}: {}", userId, error);
                throw new BadRequestException("Não foi possível criar o pagamento: " + error);
            }

            JsonNode data = root.path("data");
            String url = data.path("url").asText("").trim();
            String billId = data.path("id").asText("").trim();
            if (!StringUtils.hasText(url)) {
                log.error("AbacatePay retornou success sem url para userId={}", userId);
                throw new BadRequestException("Resposta inválida da AbacatePay.");
            }

            log.info("Checkout AbacatePay criado para usuário id={} bill={}", userId, billId);
            return new CheckoutResult(url, billId);
        } catch (RestClientResponseException e) {
            String abacateError = extrairErroAbacatePay(e.getResponseBodyAsString());
            log.error("HTTP {} ao criar checkout AbacatePay para userId={}: {}",
                    e.getStatusCode().value(), userId, e.getResponseBodyAsString());
            if (StringUtils.hasText(abacateError)) {
                throw new BadRequestException(traduzirErroAbacatePay(abacateError));
            }
            throw new BadRequestException("Falha ao comunicar com a AbacatePay. Tente novamente.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao criar checkout AbacatePay para userId={}", userId, e);
            throw new BadRequestException("Não foi possível criar o pagamento. Tente novamente.");
        }
    }

    private JsonNode abacatePayGet(String uri) throws Exception {
        String responseBody = abacatePayClient().get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(responseBody);
        if (!root.path("success").asBoolean(false)) {
            throw new BadRequestException(root.path("error").asText("Erro AbacatePay"));
        }
        return root;
    }

    private RestClient abacatePayClient() {
        return RestClient.builder()
                .baseUrl(apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String extrairErroAbacatePay(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("error").asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String traduzirErroAbacatePay(String error) {
        if ("No products found".equalsIgnoreCase(error)) {
            return "Produto não encontrado na AbacatePay. Verifique ABACATEPAY_PRODUCT_ID no .env "
                    + "(Painel AbacatePay → Produtos → copiar o id prod_...).";
        }
        return "Não foi possível criar o pagamento: " + error;
    }

    private static boolean assinaturaRootAtiva(Usuario usuario) {
        Instant expiresAt = usuario.getSubscriptionExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
