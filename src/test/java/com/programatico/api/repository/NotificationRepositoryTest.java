package com.programatico.api.repository;

import com.programatico.api.domain.Notification;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.NotificationKind;
import com.programatico.api.domain.enums.TipoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired private NotificationRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Notification maisRecente;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("notif-user")
                .email("notif@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());

        repository.save(notificacao("Antiga", Instant.now().minus(2, ChronoUnit.HOURS)));
        maisRecente = repository.save(notificacao("Recente", Instant.now()));
    }

    @Test
    void findByUsuarioOrderByCreatedAtDesc() {
        List<Notification> lista = repository.findByUsuarioOrderByCreatedAtDesc(usuario);

        assertEquals(2, lista.size());
        assertEquals("Recente", lista.get(0).getTitle());
    }

    @Test
    void findByIdAndUsuario() {
        assertTrue(repository.findByIdAndUsuario(maisRecente.getId(), usuario).isPresent());
        assertFalse(repository.findByIdAndUsuario(9999L, usuario).isPresent());
    }

    private Notification notificacao(String title, Instant createdAt) {
        return Notification.builder()
                .usuario(usuario)
                .title(title)
                .message("Mensagem")
                .kind(NotificationKind.EXERCICIO)
                .read(false)
                .createdAt(createdAt)
                .build();
    }
}
