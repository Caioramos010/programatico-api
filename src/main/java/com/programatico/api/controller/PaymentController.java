package com.programatico.api.controller;

import com.programatico.api.dto.PaymentDto;
import com.programatico.api.dto.UsuarioDto;
import com.programatico.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/checkout-url")
    public ResponseEntity<Map<String, String>> checkoutUrl(@AuthenticationPrincipal UserDetails userDetails) {
        PaymentService.CheckoutResult result = paymentService.resolveCheckoutUrl(userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "url", result.url(),
                "billId", result.billId() == null ? "" : result.billId()
        ));
    }

    @PostMapping("/sync")
    public ResponseEntity<UsuarioDto.Response> sync(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String billId = body != null ? body.get("billId") : null;
        return ResponseEntity.ok(paymentService.sincronizarAssinatura(userDetails.getUsername(), billId));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<UsuarioDto.Response> cancelar(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(paymentService.cancelarAssinatura(userDetails.getUsername()));
    }

    @GetMapping("/historico")
    public ResponseEntity<List<PaymentDto.Response>> historico(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(paymentService.listarHistorico(userDetails.getUsername()));
    }
}
