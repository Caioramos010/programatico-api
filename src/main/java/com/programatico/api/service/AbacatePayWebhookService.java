package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.ProcessedAbacateWebhook;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.repository.ProcessedAbacateWebhookRepository;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbacatePayWebhookService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final ProcessedAbacateWebhookRepository processedRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${abacatepay.webhook.secret:}")
    private String webhookSecretConfig;

    @Value("${abacatepay.webhook.hmac-key:}")
    private String hmacKey;

    @Value("${abacatepay.webhook.require-hmac:true}")
    private boolean requireHmac;

    @Value("${abacatepay.root-duration-days:30}")
    private int rootDurationDays;

    public boolean secretValido(String webhookSecretQuery) {
        if (!StringUtils.hasText(webhookSecretConfig)) {
            log.warn("abacatepay.webhook.secret não configurado; webhook rejeitado");
            return false;
        }
        return MessageDigest.isEqual(
                webhookSecretConfig.getBytes(StandardCharsets.UTF_8),
                Optional.ofNullable(webhookSecretQuery).orElse("").getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean assinaturaValida(String rawBody, String signatureHeader) {
        if (!StringUtils.hasText(hmacKey)) {
            log.warn("abacatepay.webhook.hmac-key vazio; não é possível validar HMAC");
            return false;
        }
        if (!StringUtils.hasText(signatureHeader)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Falha ao validar assinatura HMAC do webhook", e);
            return false;
        }
    }

    @Transactional
    public void processarSeNecessario(String rawBody) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);
        String eventId = root.path("id").asText(null);
        if (!StringUtils.hasText(eventId)) {
            eventId = syntheticEventId(root);
        }
        if (!StringUtils.hasText(eventId)) {
            log.warn("Webhook AbacatePay sem id nem identificador derivado; ignorando");
            return;
        }
        if (processedRepository.existsById(eventId)) {
            log.debug("Evento AbacatePay já processado: {}", eventId);
            return;
        }

        String event = root.path("event").asText("");
        JsonNode data = root.path("data");

        if (!deveLiberarPlanoPago(event, data)) {
            processedRepository.save(ProcessedAbacateWebhook.builder()
                    .eventId(eventId)
                    .processedAt(Instant.now())
                    .build());
            return;
        }

        Optional<Long> userId = extrairUserId(root, data);
        if (userId.isEmpty()) {
            log.warn("Webhook {} pago mas sem userId reconhecível (use externalId numérico ou metadata.userId)", event);
            processedRepository.save(ProcessedAbacateWebhook.builder()
                    .eventId(eventId)
                    .processedAt(Instant.now())
                    .build());
            return;
        }

        if (eventoRenovacaoAutomatica(event) && renovacaoAutomaticaCancelada(userId.get())) {
            log.info("Renovação automática ignorada (assinatura cancelada): userId={}", userId.get());
        } else {
            ativarPlanoRoot(userId.get());
        }
        processedRepository.save(ProcessedAbacateWebhook.builder()
                .eventId(eventId)
                .processedAt(Instant.now())
                .build());
    }

    private boolean eventoRenovacaoAutomatica(String event) {
        return "subscription.renewed".equals(event) || "subscription.completed".equals(event);
    }

    private boolean renovacaoAutomaticaCancelada(Long userId) {
        return usuarioRepository.findById(userId)
                .map(u -> u.getSubscriptionType() == SubscriptionType.ROOT
                        && assinaturaRootAtiva(u)
                        && Boolean.FALSE.equals(u.getSubscriptionAutoRenew()))
                .orElse(false);
    }

    private static boolean assinaturaRootAtiva(Usuario usuario) {
        Instant expiresAt = usuario.getSubscriptionExpiresAt();
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    private boolean deveLiberarPlanoPago(String event, JsonNode data) {
        return switch (event) {
            case "checkout.completed" -> "PAID".equalsIgnoreCase(texto(data, "checkout", "status"));
            case "transparent.completed" -> "PAID".equalsIgnoreCase(texto(data, "transparent", "status"));
            case "subscription.completed", "subscription.renewed" ->
                    "PAID".equalsIgnoreCase(texto(data, "payment", "status"))
                            || "PAID".equalsIgnoreCase(texto(data, "checkout", "status"));
            default -> false;
        };
    }

    private static String texto(JsonNode data, String objeto, String campo) {
        return data.path(objeto).path(campo).asText("");
    }

    private Optional<Long> extrairUserId(JsonNode root, JsonNode data) {
        Optional<Long> fromMeta = parseUserId(root.path("metadata").path("userId").asText(null));
        if (fromMeta.isPresent()) {
            return fromMeta;
        }
        Optional<Long> fromDataMeta = parseUserId(data.path("metadata").path("userId").asText(null));
        if (fromDataMeta.isPresent()) {
            return fromDataMeta;
        }
        Optional<Long> checkoutExt = parseUserId(data.path("checkout").path("externalId").asText(null));
        if (checkoutExt.isPresent()) {
            return checkoutExt;
        }
        Optional<Long> transparentExt = parseUserId(data.path("transparent").path("externalId").asText(null));
        if (transparentExt.isPresent()) {
            return transparentExt;
        }
        return parseUserId(data.path("payment").path("externalId").asText(null));
    }

    private Optional<Long> parseUserId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            return Optional.of(Long.parseLong(s));
        } catch (NumberFormatException ignored) {
            // convenção opcional user_<id>
            if (s.startsWith("user_")) {
                try {
                    return Optional.of(Long.parseLong(s.substring(5)));
                } catch (NumberFormatException ignored2) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    }

    @Transactional
    public void ativarPlanoRoot(Long userId) {
        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null) {
            log.warn("Webhook: usuário id={} não encontrado; plano não atualizado", userId);
            return;
        }
        usuario.setSubscriptionType(SubscriptionType.ROOT);
        usuario.setSubscriptionAutoRenew(true);
        usuario.setSubscriptionExpiresAt(Instant.now().plus(rootDurationDays, ChronoUnit.DAYS));
        usuarioRepository.save(usuario);
        log.info("Plano ROOT ativado via AbacatePay para usuário id={}", userId);
    }

    /**
     * Alguns exemplos na documentação omitem {@code id}; usamos checkout/transparent estáveis para idempotência.
     */
    private String syntheticEventId(JsonNode root) {
        String ev = root.path("event").asText("");
        JsonNode d = root.path("data");
        String tid = d.path("transparent").path("id").asText(null);
        if (StringUtils.hasText(tid)) {
            return "synthetic:" + ev + ":" + tid + ":" + d.path("transparent").path("updatedAt").asText("");
        }
        String cid = d.path("checkout").path("id").asText(null);
        if (StringUtils.hasText(cid)) {
            return "synthetic:" + ev + ":" + cid + ":" + d.path("checkout").path("updatedAt").asText("");
        }
        return null;
    }

    public boolean isRequireHmac() {
        return requireHmac;
    }
}
