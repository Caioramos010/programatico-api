package com.programatico.api.repository;

import com.programatico.api.domain.UserSettings;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserSettingsRepositoryTest {

    @Autowired private UserSettingsRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = usuarioRepository.save(Usuario.builder()
                .username("settings-user")
                .email("settings@test.com")
                .senha("hash")
                .idade(20)
                .ativo(true)
                .role(TipoUsuario.USER)
                .build());
    }

    @Test
    void findByUsuarioId() {
        repository.save(UserSettings.builder()
                .usuario(usuario)
                .disableAllNotifications(true)
                .twoFactorEnabled(false)
                .build());

        assertTrue(repository.findByUsuarioId(usuario.getId()).isPresent());
        assertEquals(true, repository.findByUsuarioId(usuario.getId()).get().getDisableAllNotifications());
    }
}
