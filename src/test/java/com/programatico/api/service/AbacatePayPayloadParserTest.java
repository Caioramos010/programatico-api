package com.programatico.api.service;

import com.programatico.api.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbacatePayPayloadParserTest {

    @Test
    void mapearStatusDeveReconhecerEstadosPrincipais() {
        assertEquals(PaymentStatus.PAID, AbacatePayPayloadParser.mapearStatus("PAID").orElseThrow());
        assertEquals(PaymentStatus.REFUNDED, AbacatePayPayloadParser.mapearStatus("REFUNDED").orElseThrow());
        assertEquals(PaymentStatus.FAILED, AbacatePayPayloadParser.mapearStatus("EXPIRED").orElseThrow());
        assertEquals(PaymentStatus.FAILED, AbacatePayPayloadParser.mapearStatus("FAILED").orElseThrow());
        assertEquals(PaymentStatus.PENDING, AbacatePayPayloadParser.mapearStatus("PENDING").orElseThrow());
    }

    @Test
    void eventoSuportadoDeveIncluirReembolsoEExpiracao() {
        assertTrue(AbacatePayPayloadParser.eventoSuportado("checkout.refunded"));
        assertTrue(AbacatePayPayloadParser.eventoSuportado("checkout.expired"));
        assertTrue(AbacatePayPayloadParser.eventoSuportado("checkout.failed"));
    }
}
