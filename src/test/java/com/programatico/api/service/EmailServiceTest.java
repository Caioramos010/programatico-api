package com.programatico.api.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    void enviarCodigoRedefinicaoSenhaDeveDispararEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.enviarCodigoRedefinicaoSenha("user@test.com", "user", "654321");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void enviarCodigoExclusaoContaDeveDispararEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.enviarCodigoExclusaoConta("user@test.com", "user", "999999");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void enviarCodigoVerificacaoLoginDeveDispararEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService.enviarCodigoVerificacaoLogin("user@test.com", "user", "111111");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void enviarCodigoAtivacaoDeveEscaparHtmlNoUsername() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage realMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        emailService.enviarCodigoAtivacao("user@test.com", "<script>", "123456");

        String html = extractHtml(realMessage);
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        verify(mailSender).send(realMessage);
    }

    @Test
    void enviarCodigoAtivacaoNaoDevePropagarExcecaoQuandoEnvioFalha() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("smtp indisponível")).when(mailSender).send(mimeMessage);

        assertDoesNotThrow(() ->
                emailService.enviarCodigoAtivacao("user@test.com", "user", "123456"));
    }

    private static String extractHtml(MimeMessage message) throws Exception {
        return extractHtmlFromContent(message.getContent());
    }

    private static String extractHtmlFromContent(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String part = extractHtmlFromContent(multipart.getBodyPart(i).getContent());
                if (part.contains("<html") || part.contains("Programático")) {
                    return part;
                }
            }
        }
        return content.toString();
    }
}
