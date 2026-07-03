package com.programatico.api.service;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionExpirationService {

    static final String TITULO_EXPIRACAO = "Sua assinatura Root expirou";
    static final String MENSAGEM_EXPIRACAO =
            "Seu plano Root chegou ao fim. Renove para voltar a ter vidas ilimitadas e todos os benefícios.";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationService.class);

    private final UsuarioRepository usuarioRepository;
    private final NotificationService notificationService;
    private final UserSettingsService userSettingsService;

    /**
     * Normaliza usuários ROOT com {@code subscriptionExpiresAt} no passado para FREE
     * e envia notificação in-app quando permitido nas preferências.
     *
     * @return quantidade de usuários rebaixados neste ciclo
     */
    @Transactional
    public int processarAssinaturasExpiradas() {
        Instant agora = Instant.now();
        List<Usuario> expirados = usuarioRepository
                .findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
                        SubscriptionType.ROOT, agora);

        for (Usuario usuario : expirados) {
            usuario.setSubscriptionType(SubscriptionType.FREE);
            usuario.setSubscriptionExpiresAt(null);
            usuarioRepository.save(usuario);

            if (userSettingsService.podeNotificar(
                    usuario.getUsername(), UserSettingsService.NotificationKind.SUBSCRIPTION)) {
                notificationService.criarNotificacaoSistema(
                        usuario,
                        TITULO_EXPIRACAO,
                        MENSAGEM_EXPIRACAO,
                        NotificationKind.SUBSCRIPTION
                );
            }
        }

        if (!expirados.isEmpty()) {
            log.info("Assinaturas ROOT expiradas normalizadas para FREE: total={}", expirados.size());
        }

        return expirados.size();
    }
}
