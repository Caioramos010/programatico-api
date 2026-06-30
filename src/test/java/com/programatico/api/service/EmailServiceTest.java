package com.programatico.api.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private UserSettingsService userSettingsService;
    @Mock private MimeMessage mimeMessage;

    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "remetente", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.test");
    }

    @Test
    void enviarCodigoAtivacaoDeveDispararEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.enviarCodigoAtivacao("user@test.com", "user", "123456");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void enviarEmailInformativoDeveRespeitarPreferenciaDoUsuario() {
        when(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.EMAIL))
                .thenReturn(false);

        emailService.enviarEmailInformativo("user@test.com", "user", "Assunto", "<p>html</p>");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void enviarEmailInformativoDeveEnviarQuandoPermitido() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(userSettingsService.podeNotificar("user", UserSettingsService.NotificationKind.EMAIL))
                .thenReturn(true);

        emailService.enviarEmailInformativo("user@test.com", "user", "Assunto", "<p>html</p>");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void enviarCodigoAtivacaoDeveEscaparHtmlNoUsername() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.enviarCodigoAtivacao("user@test.com", "<script>", "123456");

        verify(mailSender).send(mimeMessage);
    }
}
