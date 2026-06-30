package com.programatico.api.service;

import com.programatico.api.domain.TwoFactorBackupCode;
import com.programatico.api.domain.Usuario;
import com.programatico.api.repository.TwoFactorBackupCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BackupCodeService {

    private static final String CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;

    private final TwoFactorBackupCodeRepository backupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.two-factor.backup-code-count:10}")
    private int backupCodeCount;

    @Transactional
    public List<String> gerarParaUsuario(Usuario usuario) {
        backupCodeRepository.deleteByUsuarioId(usuario.getId());
        Instant agora = Instant.now();
        List<String> plainCodes = new ArrayList<>(backupCodeCount);
        for (int i = 0; i < backupCodeCount; i++) {
            String plain = gerarCodigoPlain();
            plainCodes.add(formatarExibicao(plain));
            backupCodeRepository.save(TwoFactorBackupCode.builder()
                    .usuario(usuario)
                    .codeHash(passwordEncoder.encode(plain))
                    .createdAt(agora)
                    .build());
        }
        return plainCodes;
    }

    @Transactional(readOnly = true)
    public boolean temCodigosDisponiveis(Usuario usuario) {
        return backupCodeRepository.countByUsuarioIdAndUsedAtIsNull(usuario.getId()) > 0;
    }

    @Transactional(readOnly = true)
    public int contarDisponiveis(Usuario usuario) {
        return (int) backupCodeRepository.countByUsuarioIdAndUsedAtIsNull(usuario.getId());
    }

    @Transactional
    public boolean tentarConsumir(Usuario usuario, String codigoInformado) {
        if (!StringUtils.hasText(codigoInformado)) {
            return false;
        }
        String normalizado = normalizar(codigoInformado);
        if (normalizado.length() != CODE_LENGTH) {
            return false;
        }
        List<TwoFactorBackupCode> disponiveis = backupCodeRepository.findByUsuarioIdAndUsedAtIsNull(usuario.getId());
        for (TwoFactorBackupCode registro : disponiveis) {
            if (passwordEncoder.matches(normalizado, registro.getCodeHash())) {
                registro.setUsedAt(Instant.now());
                backupCodeRepository.save(registro);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void invalidarTodos(Usuario usuario) {
        backupCodeRepository.deleteByUsuarioId(usuario.getId());
    }

    public static boolean pareceCodigoBackup(String codigo) {
        return normalizar(codigo).length() >= CODE_LENGTH;
    }

    static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return codigo.trim()
                .replace("-", "")
                .replace(" ", "")
                .toUpperCase();
    }

    private String gerarCodigoPlain() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(secureRandom.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    private static String formatarExibicao(String plain) {
        return plain.substring(0, 4) + "-" + plain.substring(4);
    }
}
