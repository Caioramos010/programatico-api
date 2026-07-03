package com.programatico.api.repository;

import com.programatico.api.domain.Payment;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUsuarioOrderByCreatedAtDesc(Usuario usuario);

    Optional<Payment> findByBillId(String billId);

    boolean existsByBillId(String billId);
}
