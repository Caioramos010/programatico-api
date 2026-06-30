package com.programatico.api.service;

import com.programatico.api.domain.TwoFactorBackupCode;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.TwoFactorBackupCodeRepository;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupCodeServiceTest {

    @Mock
    private TwoFactorBackupCodeRepository backupCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BackupCodeService backupCodeService;

    private final PasswordEncoder realEncoder = new BCryptPasswordEncoder();

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupCodeService, "backupCodeCount", 3);
        usuario = Usuario.builder()
                .id(1L)
                .username("user")
                .email("user@test.com")
                .role(TipoUsuario.USER)
                .build();
    }

    @Test
    void deveGerarCodigosFormatados() {
        when(passwordEncoder.encode(any())).thenAnswer(inv -> realEncoder.encode(inv.getArgument(0)));
        when(backupCodeRepository.save(any(TwoFactorBackupCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<String> codes = backupCodeService.gerarParaUsuario(usuario);

        assertEquals(3, codes.size());
        codes.forEach(code -> assertTrue(code.matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}")));
        verify(backupCodeRepository).deleteByUsuarioId(1L);
        verify(backupCodeRepository, atLeastOnce()).save(any(TwoFactorBackupCode.class));
    }

    @Test
    void deveConsumirCodigoUmaVez() {
        String plain = "ABCD2345";
        TwoFactorBackupCode registro = TwoFactorBackupCode.builder()
                .id(10L)
                .usuario(usuario)
                .codeHash(realEncoder.encode(plain))
                .build();
        when(backupCodeRepository.findByUsuarioIdAndUsedAtIsNull(1L)).thenReturn(List.of(registro));
        when(backupCodeRepository.save(any(TwoFactorBackupCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.matches(any(), any())).thenAnswer(inv ->
                realEncoder.matches(inv.getArgument(0), inv.getArgument(1)));

        assertTrue(backupCodeService.tentarConsumir(usuario, "ABCD-2345"));

        ArgumentCaptor<TwoFactorBackupCode> captor = ArgumentCaptor.forClass(TwoFactorBackupCode.class);
        verify(backupCodeRepository).save(captor.capture());
        assertTrue(captor.getValue().getUsedAt() != null);

        when(backupCodeRepository.findByUsuarioIdAndUsedAtIsNull(1L)).thenReturn(List.of());
        assertFalse(backupCodeService.tentarConsumir(usuario, "ABCD-2345"));
    }

    @Test
    void pareceCodigoBackupDeveDetectarFormatoLongo() {
        assertTrue(BackupCodeService.pareceCodigoBackup("ABCD-2345"));
        assertFalse(BackupCodeService.pareceCodigoBackup("123456"));
    }
}
