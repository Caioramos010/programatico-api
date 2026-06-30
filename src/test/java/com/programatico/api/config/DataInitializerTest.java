package com.programatico.api.config;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataInitializer, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(dataInitializer, "adminPassword", "Admin@123");
        ReflectionTestUtils.setField(dataInitializer, "adminUsername", "admin");
    }

    @Test
    void runDeveCriarAdminQuandoNaoExiste() {
        when(usuarioRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded");

        dataInitializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(TipoUsuario.ADMIN, admin.getRole());
        org.junit.jupiter.api.Assertions.assertEquals("admin@test.com", admin.getEmail());
    }

    @Test
    void runDeveIgnorarQuandoAdminJaExiste() {
        when(usuarioRepository.existsByEmail("admin@test.com")).thenReturn(true);

        dataInitializer.run(new DefaultApplicationArguments());

        verify(usuarioRepository, never()).save(any());
    }
}
