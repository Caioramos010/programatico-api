package com.programatico.api.testsupport;

import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.UserSettingsRepository;
import com.programatico.api.repository.UsuarioRepository;

/**
 * Remove dependências de {@code users} antes de {@code deleteAll()} na tabela,
 * evitando violação de FK quando requisições MockMvc commitam fora da transação do teste.
 */
public final class IntegrationTestDbCleaner {

    private IntegrationTestDbCleaner() {
    }

    public static void limparUsuarios(
            UsuarioRepository usuarioRepository,
            UserSettingsRepository userSettingsRepository) {
        limparUsuarios(usuarioRepository, userSettingsRepository, null);
    }

    public static void limparUsuarios(
            UsuarioRepository usuarioRepository,
            UserSettingsRepository userSettingsRepository,
            PaymentRepository paymentRepository) {
        if (paymentRepository != null) {
            paymentRepository.deleteAll();
        }
        if (userSettingsRepository != null) {
            userSettingsRepository.deleteAll();
        }
        usuarioRepository.deleteAll();
    }
}
