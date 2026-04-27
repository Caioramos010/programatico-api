package com.programatico.api.config;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o usuário administrador padrão na inicialização caso ele não exista.
 * As credenciais são lidas via variáveis de ambiente:
 *   ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_USERNAME
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@programatico.com}")
    private String adminEmail;

    @Value("${admin.password:Admin@123456}")
    private String adminPassword;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByEmail(adminEmail)) {
            log.info("Usuário admin já existe — seed ignorado.");
            return;
        }

        Usuario admin = Usuario.builder()
                .email(adminEmail)
                .username(adminUsername)
                .senha(passwordEncoder.encode(adminPassword))
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build();

        usuarioRepository.save(admin);
        log.info("Usuário admin criado com sucesso: email={}", adminEmail);
    }
}
