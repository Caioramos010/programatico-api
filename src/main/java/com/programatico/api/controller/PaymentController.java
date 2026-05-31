package com.programatico.api.controller;

import com.programatico.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/checkout-url")
    public ResponseEntity<Map<String, String>> checkoutUrl(@AuthenticationPrincipal UserDetails userDetails) {
        String url = paymentService.resolveCheckoutUrl(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("url", url));
    }
}
