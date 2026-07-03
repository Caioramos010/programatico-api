package com.programatico.api.service;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        when(userSettingsService.podeNotificar(anyString(), any(UserSettingsService.NotificationKind.class)))
                .thenReturn(true);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());

        emailService = new EmailService(mailSender, userSettingsService);
        ReflectionTestUtils.setField(emailService, "remetente", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.test");
    }

    @Test
    void deveEntregarEmailDeAtivacaoViaSmtp() throws Exception {
        emailService.enviarCodigoAtivacao("user@test.com", "Maria", "123456");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        assertEquals("user@test.com", received[0].getAllRecipients()[0].toString());
        assertTrue(MimeUtility.decodeText(received[0].getSubject()).contains("Ative"));
        assertTrue(GreenMailUtil.getBody(received[0]).contains("123456"));
    }

    @Test
    void deveEntregarEmailInformativoQuandoPermitido() throws Exception {
        emailService.enviarEmailInformativo("aluno@test.com", "aluno", "Novidades", "<p>Conteudo informativo</p>");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        assertEquals("Novidades", MimeUtility.decodeText(received[0].getSubject()));
        assertTrue(GreenMailUtil.getBody(received[0]).contains("Conteudo informativo"));
    }
}
