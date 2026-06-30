package com.programatico.api.testsupport;

import com.programatico.api.repository.PaymentRepository;
import com.programatico.api.repository.TwoFactorBackupCodeRepository;
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
        limparUsuarios(usuarioRepository, userSettingsRepository, null, null);
    }

    public static void limparUsuarios(
            UsuarioRepository usuarioRepository,
            UserSettingsRepository userSettingsRepository,
            PaymentRepository paymentRepository) {
        limparUsuarios(usuarioRepository, userSettingsRepository, paymentRepository, null);
    }

    public static void limparUsuarios(
            UsuarioRepository usuarioRepository,
            UserSettingsRepository userSettingsRepository,
            PaymentRepository paymentRepository,
            TwoFactorBackupCodeRepository backupCodeRepository) {
        if (paymentRepository != null) {
            paymentRepository.deleteAll();
        }
        if (backupCodeRepository != null) {
            backupCodeRepository.deleteAll();
        }
        if (userSettingsRepository != null) {
            userSettingsRepository.deleteAll();
        }
        usuarioRepository.deleteAll();
    }
}
