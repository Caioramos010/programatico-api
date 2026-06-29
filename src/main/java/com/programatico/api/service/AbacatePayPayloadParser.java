package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.programatico.api.domain.enums.PaymentMethod;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class AbacatePayPayloadParser {

    private AbacatePayPayloadParser() {}

    static JsonNode noPrincipal(String event, JsonNode data) {
        return switch (event) {
            case "checkout.completed" -> data.path("checkout");
            case "transparent.completed" -> data.path("transparent");
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
