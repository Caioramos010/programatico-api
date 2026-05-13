package com.programatico.api.controller;

import com.programatico.api.service.AbacatePayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class AbacatePayWebhookController {

    private final AbacatePayWebhookService abacatePayWebhookService;

    @PostMapping(value = "/abacatepay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receber(
            @RequestParam("webhookSecret") String webhookSecret,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature
    ) {
        if (!abacatePayWebhookService.secretValido(webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (abacatePayWebhookService.isRequireHmac()
                && !abacatePayWebhookService.assinaturaValida(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            abacatePayWebhookService.processarSeNecessario(rawBody);
        } catch (Exception e) {
            log.error("Erro ao processar webhook AbacatePay", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }
}
