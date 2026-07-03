package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

final class AbacatePayPayloadParser {

    private AbacatePayPayloadParser() {}

    static boolean eventoSuportado(String event) {
        if (!StringUtils.hasText(event)) {
            return false;
        }
        return switch (event) {
            case "checkout.completed", "transparent.completed",
                 "subscription.completed", "subscription.renewed",
                 "checkout.refunded", "subscription.refunded", "payment.refunded",
                 "checkout.expired", "checkout.failed", "payment.failed" -> true;
            default -> event.contains("refunded") || event.contains("expired") || event.contains("failed");
        };
    }

    static Optional<PaymentStatus> resolverStatusPagamento(String event, JsonNode data) {
        if (event.contains("refunded")) {
            return Optional.of(PaymentStatus.REFUNDED);
        }
        if (event.contains("expired")) {
            return Optional.of(PaymentStatus.FAILED);
        }
        if (event.contains("failed")) {
            return Optional.of(PaymentStatus.FAILED);
        }

        String rawStatus = extrairStatusRaw(event, data);
        if (!StringUtils.hasText(rawStatus)) {
            return Optional.empty();
        }
        return mapearStatus(rawStatus);
    }

    static String extrairStatusRaw(String event, JsonNode data) {
        JsonNode node = noPrincipal(event, data);
        if (!node.isMissingNode()) {
            String status = node.path("status").asText("").trim();
            if (StringUtils.hasText(status)) {
                return status;
            }
        }
        return data.path("status").asText("").trim();
    }

    static Optional<PaymentStatus> mapearStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return switch (raw.toUpperCase()) {
            case "PAID", "APPROVED", "COMPLETED", "SUCCEEDED" -> Optional.of(PaymentStatus.PAID);
            case "REFUNDED", "CANCELLED", "CANCELED" -> Optional.of(PaymentStatus.REFUNDED);
            case "FAILED", "EXPIRED", "DECLINED", "REJECTED", "CANCELLED_PAYMENT" -> Optional.of(PaymentStatus.FAILED);
            case "PENDING", "WAITING", "PROCESSING", "OPEN" -> Optional.of(PaymentStatus.PENDING);
            default -> Optional.empty();
        };
    }

    static JsonNode noPrincipal(String event, JsonNode data) {
        return switch (event) {
            case "checkout.completed" -> data.path("checkout");
            case "transparent.completed" -> data.path("transparent");
            case "checkout.refunded", "checkout.expired", "checkout.failed" -> data.path("checkout");
            case "payment.refunded", "payment.failed" -> data.path("payment");
            case "subscription.refunded" -> {
                JsonNode payment = data.path("payment");
                yield payment.isMissingNode() ? data.path("checkout") : payment;
            }
            default -> {
                JsonNode payment = data.path("payment");
                yield payment.isMissingNode() ? data.path("checkout") : payment;
            }
        };
    }

    static String extrairBillId(String event, JsonNode data) {
        JsonNode node = noPrincipal(event, data);
        if (node.isMissingNode()) {
            return "";
        }
        String id = node.path("id").asText("").trim();
        if (StringUtils.hasText(id)) {
            return id;
        }
        return node.path("billId").asText("").trim();
    }

    static BigDecimal extrairValor(String event, JsonNode data) {
        JsonNode node = noPrincipal(event, data);
        if (node.isMissingNode()) {
            return BigDecimal.ZERO;
        }
        if (node.has("amount") && !node.path("amount").isNull()) {
            return centavosParaReais(node.path("amount"));
        }
        if (node.has("total") && !node.path("total").isNull()) {
            return centavosParaReais(node.path("total"));
        }
        return BigDecimal.ZERO;
    }

    static PaymentMethod extrairMetodo(String event, JsonNode data) {
        JsonNode node = noPrincipal(event, data);
        if (node.isMissingNode()) {
            return PaymentMethod.UNKNOWN;
        }
        String raw = node.path("method").asText("").trim();
        if (!StringUtils.hasText(raw)) {
            raw = node.path("paymentMethod").asText("").trim();
        }
        return parseMetodo(raw);
    }

    private static BigDecimal centavosParaReais(JsonNode amountNode) {
        if (amountNode.isIntegralNumber()) {
            return BigDecimal.valueOf(amountNode.asLong()).movePointLeft(2);
        }
        try {
            return new BigDecimal(amountNode.asText("0").trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static PaymentMethod parseMetodo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return PaymentMethod.UNKNOWN;
        }
        return switch (raw.toUpperCase()) {
            case "PIX" -> PaymentMethod.PIX;
            case "CARD", "CREDIT_CARD", "DEBIT_CARD" -> PaymentMethod.CARD;
            default -> PaymentMethod.UNKNOWN;
        };
    }
}
