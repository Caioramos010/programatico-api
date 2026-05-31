package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

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

    public String resolveCheckoutUrl(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (usuario.getSubscriptionType() == SubscriptionType.ROOT) {
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
        return url;
    }

    private String criarCheckoutAbacatePay(Usuario usuario) {
        String userId = String.valueOf(usuario.getId());
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("id", productId.trim(), "quantity", 1)));
        body.put("externalId", userId);
        body.put("metadata", Map.of("userId", userId));
        body.put("returnUrl", base + "/seja-root");
        body.put("completionUrl", base + "/aprender");
        body.put("methods", List.of("PIX", "CARD"));

        RestClient client = RestClient.builder()
                .baseUrl(apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String responseBody = client.post()
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

            String url = root.path("data").path("url").asText("").trim();
            if (!StringUtils.hasText(url)) {
                log.error("AbacatePay retornou success sem url para userId={}", userId);
                throw new BadRequestException("Resposta inválida da AbacatePay.");
            }

            log.info("Checkout AbacatePay criado para usuário id={}", userId);
            return url;
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
}
