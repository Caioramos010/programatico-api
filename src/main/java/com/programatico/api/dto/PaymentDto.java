package com.programatico.api.dto;

import com.programatico.api.domain.Payment;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDto {

    private PaymentDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private BigDecimal amount;
        private PaymentStatus status;
        private PaymentMethod method;
        private String billId;
        private Instant createdAt;

        public static Response fromEntity(Payment payment) {
            return Response.builder()
                    .id(payment.getId())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .method(payment.getMethod())
                    .billId(payment.getBillId())
                    .createdAt(payment.getCreatedAt())
                    .build();
        }
    }
}
