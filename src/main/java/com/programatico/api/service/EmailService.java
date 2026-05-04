package com.programatico.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ─────────────────────────────────────────────────────────────
    //  Ativação de conta
    // ─────────────────────────────────────────────────────────────

    @Async
    public void enviarCodigoAtivacao(String destinatario, String username, String codigo) {
        String assunto = "✅ Ative sua conta no Programático";

        String conteudo = """
                <p style="margin:0 0 16px;color:#ffffffcc;font-size:16px;line-height:1.6;">
                  Olá, <strong style="color:#ffffff;">%s</strong>!
                </p>
                <p style="margin:0 0 24px;color:#ffffffcc;font-size:15px;line-height:1.6;">
                  Estamos felizes em ter você por aqui. Para começar sua jornada no
                  <strong style="color:#ffffff;">Programático</strong>, confirme seu e-mail com o código abaixo:
                </p>
                """.formatted(escapeHtml(username));

        String nota = """
                <p style="margin:0;color:#9ca3af;font-size:13px;line-height:1.5;text-align:center;">
                  O código não expira, mas pode ser regenerado a qualquer momento.<br>
                  Se você não criou uma conta, apenas ignore este e-mail.
                </p>
                """;

        enviarHtml(destinatario, assunto, montarEmail(conteudo, codigo, nota, "#11604d"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Redefinição de senha
    // ─────────────────────────────────────────────────────────────

    @Async
    public void enviarCodigoRedefinicaoSenha(String destinatario, String username, String codigo) {
        String assunto = "🔑 Redefinição de senha — Programático";

        String conteudo = """
                <p style="margin:0 0 16px;color:#ffffffcc;font-size:16px;line-height:1.6;">
                  Olá, <strong style="color:#ffffff;">%s</strong>!
                </p>
                <p style="margin:0 0 24px;color:#ffffffcc;font-size:15px;line-height:1.6;">
                  Recebemos uma solicitação de <strong style="color:#ffffff;">redefinição de senha</strong>
                  para sua conta. Use o código abaixo para criar uma nova senha:
                </p>
                """.formatted(escapeHtml(username));

        String nota = """
                <p style="margin:0;color:#9ca3af;font-size:13px;line-height:1.5;text-align:center;">
                  Este código expira em <strong style="color:#ffffffcc;">24 horas</strong>.<br>
                  Se você não solicitou a redefinição, ignore este e-mail — sua senha continua a mesma.
                </p>
                """;

        enviarHtml(destinatario, assunto, montarEmail(conteudo, codigo, nota, "#11604d"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Exclusão de conta
    // ─────────────────────────────────────────────────────────────

    @Async
    public void enviarCodigoExclusaoConta(String destinatario, String username, String codigo) {
        String assunto = "⚠️ Confirmação de exclusão de conta — Programático";

        String conteudo = """
                <p style="margin:0 0 16px;color:#ffffffcc;font-size:16px;line-height:1.6;">
                  Olá, <strong style="color:#ffffff;">%s</strong>!
                </p>
                <p style="margin:0 0 16px;color:#ffffffcc;font-size:15px;line-height:1.6;">
                  Recebemos uma solicitação para <strong style="color:#ff6b6b;">excluir permanentemente</strong>
                  sua conta no Programático. Esta ação não pode ser desfeita.
                </p>
                <p style="margin:0 0 24px;color:#ffffffcc;font-size:15px;line-height:1.6;">
                  Se foi você quem solicitou, use o código abaixo para confirmar:
                </p>
                """.formatted(escapeHtml(username));

        String nota = """
                <p style="margin:0;color:#9ca3af;font-size:13px;line-height:1.5;text-align:center;">
                  Este código expira em <strong style="color:#ffffffcc;">24 horas</strong>.<br>
                  Se você não solicitou a exclusão, ignore este e-mail — sua conta continua ativa e segura.
                </p>
                """;

        enviarHtml(destinatario, assunto, montarEmail(conteudo, codigo, nota, "#88181a"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Verificação de login (2ª etapa)
    // ─────────────────────────────────────────────────────────────

    @Async
    public void enviarCodigoVerificacaoLogin(String destinatario, String username, String codigo) {
        String assunto = "🔐 Código de verificação — Programático";

        String conteudo = """
                <p style="margin:0 0 16px;color:#ffffffcc;font-size:16px;line-height:1.6;">
                  Olá, <strong style="color:#ffffff;">%s</strong>!
                </p>
                <p style="margin:0 0 24px;color:#ffffffcc;font-size:15px;line-height:1.6;">
                  Detectamos uma tentativa de <strong style="color:#ffffff;">acesso à sua conta</strong>.
                  Use o código abaixo para concluir o login:
                </p>
                """.formatted(escapeHtml(username));

        String nota = """
                <p style="margin:0;color:#9ca3af;font-size:13px;line-height:1.5;text-align:center;">
                  Este código expira em <strong style="color:#ffffffcc;">1 hora</strong>.<br>
                  Se você não tentou entrar, altere sua senha e ignore este e-mail.
                </p>
                """;

        enviarHtml(destinatario, assunto, montarEmail(conteudo, codigo, nota, "#11604d"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Template HTML base
    // ─────────────────────────────────────────────────────────────

    private String montarEmail(String conteudo, String codigo, String nota,
                                String accentColor) {
        String mascotUrl = escapeHtml(frontendUrl) + "/mascot-email.png";
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <meta http-equiv="X-UA-Compatible" content="IE=edge">
                  <title>Programático</title>
                </head>
                <body style="margin:0;padding:0;background-color:#1a1f33;font-family:'Segoe UI',Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background-color:#1a1f33;min-height:100vh;">
                    <tr>
                      <td align="center" style="padding:48px 20px;">

                        <!-- Card container -->
                        <table width="560" cellpadding="0" cellspacing="0" border="0"
                               style="max-width:560px;width:100%%;border-radius:20px;overflow:hidden;
                                      box-shadow:0 20px 60px rgba(0,0,0,0.5);">

                          <!-- ── Header ── -->
                          <tr>
                            <td align="center"
                                style="background-color:%s;padding:36px 40px 28px;">
                              <img src="%s" alt="Mascote Programático"
                                   width="72" height="72"
                                   style="display:block;margin:0 auto 12px;border-radius:50%%;
                                          object-fit:cover;border:3px solid rgba(255,255,255,0.2);" />
                              <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;
                                         letter-spacing:-0.5px;line-height:1.2;">
                                Programático
                              </h1>
                              <p style="margin:6px 0 0;color:#ffffffb3;font-size:13px;
                                        letter-spacing:0.5px;text-transform:uppercase;">
                                Aprenda · Evolua · Domine
                              </p>
                            </td>
                          </tr>

                          <!-- ── Body ── -->
                          <tr>
                            <td style="background-color:#1e2538;padding:36px 40px 28px;">
                              %s

                              <!-- Código destacado -->
                              <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td align="center">
                                    <div style="display:inline-block;background-color:#293046;
                                                border:2px solid %s;border-radius:14px;
                                                padding:22px 40px;margin:0 auto;">
                                      <p style="margin:0 0 4px;color:#9ca3af;font-size:11px;
                                                text-transform:uppercase;letter-spacing:2px;">
                                        Seu código
                                      </p>
                                      <p style="margin:0;color:#ffffff;font-size:42px;
                                                font-weight:700;letter-spacing:12px;
                                                font-family:'Courier New',monospace;">
                                        %s
                                      </p>
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- ── Nota / Aviso ── -->
                          <tr>
                            <td style="background-color:#232a40;padding:20px 40px;">
                              %s
                            </td>
                          </tr>

                          <!-- ── Footer ── -->
                          <tr>
                            <td align="center"
                                style="background-color:#1a1f33;padding:24px 40px;
                                       border-top:1px solid #3b4a6b;">
                              <p style="margin:0 0 6px;color:#9ca3af;font-size:12px;">
                                © 2025 Programático. Todos os direitos reservados.
                              </p>
                              <p style="margin:0;color:#6b7280;font-size:11px;">
                                Este é um e-mail automático. Por favor, não responda.
                              </p>
                            </td>
                          </tr>

                        </table>
                        <!-- /Card container -->

                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(accentColor, mascotUrl, conteudo, accentColor, escapeHtml(codigo), nota);
    }

    // ─────────────────────────────────────────────────────────────
    //  Envio
    // ─────────────────────────────────────────────────────────────

    private void enviarHtml(String destinatario, String assunto, String html) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(html, true);
            mailSender.send(mensagem);
            log.info("E-mail enviado para {} | assunto: {}", destinatario, assunto);
        } catch (MessagingException | MailException ex) {
            log.error("Falha ao enviar e-mail para {} | assunto: {} | erro: {}",
                    destinatario, assunto, ex.getMessage(), ex);
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
