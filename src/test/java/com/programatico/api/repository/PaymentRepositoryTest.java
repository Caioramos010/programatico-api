package com.programatico.api.repository;

import com.programatico.api.domain.Payment;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.PaymentMethod;
import com.programatico.api.domain.enums.PaymentStatus;
import com.programatico.api.domain.enums.TipoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("pay-user")
                .email("pay@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
    }

    @Test
    void findByBillIdEExistsByBillId() {
        paymentRepository.save(payment("bill-abc", Instant.now()));

        assertTrue(paymentRepository.existsByBillId("bill-abc"));
        assertTrue(paymentRepository.findByBillId("bill-abc").isPresent());
        assertFalse(paymentRepository.existsByBillId("inexistente"));
    }

    @Test
    void findByUsuarioOrderByCreatedAtDesc() throws InterruptedException {
        Instant older = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant newer = Instant.now();
        paymentRepository.save(payment("bill-old", older));
        paymentRepository.save(payment("bill-new", newer));

        List<Payment> historico = paymentRepository.findByUsuarioOrderByCreatedAtDesc(usuario);

        assertEquals(2, historico.size());
        assertEquals("bill-new", historico.get(0).getBillId());
        assertEquals("bill-old", historico.get(1).getBillId());
    }

    private Payment payment(String billId, Instant createdAt) {
        return Payment.builder()
                .usuario(usuario)
                .amount(new BigDecimal("29.90"))
                .status(PaymentStatus.PAID)
                .method(PaymentMethod.PIX)
                .billId(billId)
                .createdAt(createdAt)
                .build();
    }
}
