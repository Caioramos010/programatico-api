package com.programatico.api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    @Async
    public void enviarCodigoAtivacao(String destinatario, String username, String codigo) {
        String assunto = "Ative sua conta no Programático";
        String corpo = """
                Olá, %s!

                Seu código de ativação: %s

                Digite esse código na plataforma para ativar sua conta.
                O código não expira, mas pode ser substituído por um novo se você solicitar reenvio.

                Se você não criou uma conta no Programático, apenas ignore este e-mail.

                Equipe Programático
                """.formatted(username, codigo);

        enviar(destinatario, assunto, corpo);
    }

    @Async
    public void enviarCodigoRedefinicaoSenha(String destinatario, String username, String codigo) {
        String assunto = "Redefinição de senha — Programático";
        String corpo = """
                Olá, %s!

                Seu código para redefinir a senha: %s

                O código expira em 24 horas.
                Se você não solicitou a redefinição, ignore este e-mail — sua senha continua a mesma.

                Equipe Programático
                """.formatted(username, codigo);

        enviar(destinatario, assunto, corpo);
    }

    private void enviar(String destinatario, String assunto, String corpo) {
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destinatario);
            mensagem.setSubject(assunto);
            mensagem.setText(corpo);
            mailSender.send(mensagem);
            log.info("E-mail enviado para {} | assunto: {}", destinatario, assunto);
        } catch (MailException ex) {
            log.error("Falha ao enviar e-mail para {} | assunto: {} | erro: {}", destinatario, assunto, ex.getMessage(), ex);
        }
    }
}
