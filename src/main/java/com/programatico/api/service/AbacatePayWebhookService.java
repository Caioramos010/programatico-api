package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Payment;
import com.programatico.api.domain.ProcessedAbacateWebhook;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.repository.PaymentRepository;
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
import java.math.BigDecimal;
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
    private final PaymentRepository paymentRepository;

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

        if (!AbacatePayPayloadParser.eventoSuportado(event)) {
            marcarProcessado(eventId);
            return;
        }

        Optional<PaymentStatus> statusOpt = AbacatePayPayloadParser.resolverStatusPagamento(event, data);
        if (statusOpt.isEmpty()) {
            marcarProcessado(eventId);
            return;
        }

        Optional<Long> userId = extrairUserId(root, data);
        PaymentStatus status = statusOpt.get();

        if (userId.isPresent()) {
            processarPorStatus(userId.get(), event, data, eventId, status);
        } else if (status == PaymentStatus.PAID) {
            log.warn("Webhook {} pago mas sem userId reconhecível (use externalId numérico ou metadata.userId)", event);
        }

        marcarProcessado(eventId);
    }

    private void processarPorStatus(
            Long userId,
            String event,
            JsonNode data,
            String eventId,
            PaymentStatus status
    ) {
        switch (status) {
            case PAID -> processarPagamentoConfirmado(userId, event, data, eventId);
            case REFUNDED -> processarReembolso(userId, event, data, eventId);
            case FAILED -> upsertPagamento(userId, event, data, eventId, PaymentStatus.FAILED);
            case PENDING -> upsertPagamento(userId, event, data, eventId, PaymentStatus.PENDING);
        }
    }

    private void processarPagamentoConfirmado(Long userId, String event, JsonNode data, String eventId) {
        upsertPagamento(userId, event, data, eventId, PaymentStatus.PAID);

        if (eventoRenovacaoAutomatica(event) && renovacaoAutomaticaCancelada(userId)) {
            log.info("Renovação automática ignorada (assinatura cancelada): userId={}", userId);
        } else {
            ativarPlanoRoot(userId);
        }
    }

    private void processarReembolso(Long userId, String event, JsonNode data, String eventId) {
        upsertPagamento(userId, event, data, eventId, PaymentStatus.REFUNDED);
        encerrarPlanoRoot(userId, "reembolso");
    }

    private void encerrarPlanoRoot(Long userId, String motivo) {
        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null || usuario.getSubscriptionType() != SubscriptionType.ROOT) {
            return;
        }
        usuario.setSubscriptionType(SubscriptionType.FREE);
        usuario.setSubscriptionExpiresAt(null);
        usuario.setSubscriptionAutoRenew(false);
        usuarioRepository.save(usuario);
        log.info("Plano ROOT encerrado via webhook ({}): userId={}", motivo, userId);
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

    private void upsertPagamento(Long userId, String event, JsonNode data, String eventId, PaymentStatus status) {
        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null) {
            return;
        }

        String billId = AbacatePayPayloadParser.extrairBillId(event, data);
        if (!StringUtils.hasText(billId)) {
            billId = "event:" + eventId;
        }

        Optional<Payment> existente = paymentRepository.findByBillId(billId);
        if (existente.isPresent()) {
            Payment payment = existente.get();
            payment.setStatus(status);
            paymentRepository.save(payment);
            log.info("Comprovante atualizado: userId={}, billId={}, status={}", userId, billId, status);
            return;
        }

        BigDecimal amount = AbacatePayPayloadParser.extrairValor(event, data);
        PaymentMethod method = AbacatePayPayloadParser.extrairMetodo(event, data);

        paymentRepository.save(Payment.builder()
                .usuario(usuario)
                .amount(amount)
                .status(status)
                .method(method)
                .billId(billId)
                .build());
        log.info("Comprovante registrado: userId={}, billId={}, status={}, amount={}", userId, billId, status, amount);
    }

    private void marcarProcessado(String eventId) {
        processedRepository.save(ProcessedAbacateWebhook.builder()
                .eventId(eventId)
                .processedAt(Instant.now())
                .build());
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
        String pid = d.path("payment").path("id").asText(null);
        if (StringUtils.hasText(pid)) {
            return "synthetic:" + ev + ":" + pid + ":" + d.path("payment").path("updatedAt").asText("");
        }
        return null;
    }

    public boolean isRequireHmac() {
        return requireHmac;
    }
}
