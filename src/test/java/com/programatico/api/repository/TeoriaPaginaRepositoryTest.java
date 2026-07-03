package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class TeoriaPaginaRepositoryTest {

    @Autowired private TeoriaPaginaRepository repository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private TrackRepository trackRepository;

    private Modulo modulo;

    @BeforeEach
    void setUp() {
        Track track = trackRepository.save(Track.builder()
                .title("Trilha")
                .description("Desc")
                .displayOrder(1)
                .build());
        modulo = moduloRepository.save(Modulo.builder()
                .track(track)
                .title("Teoria")
                .moduleType(ModuleType.STUDY)
                .displayOrder(1)
                .build());

        repository.save(pagina("Página 2", 2));
        repository.save(pagina("Página 1", 1));
    }

    @Test
    void findByModuloOrderByDisplayOrderAscECount() {
        List<TeoriaPagina> paginas = repository.findByModuloOrderByDisplayOrderAsc(modulo);
        assertEquals(2, paginas.size());
        assertEquals("Página 1", paginas.get(0).getTitle());
        assertEquals(2L, repository.countByModulo(modulo));
    }

    private TeoriaPagina pagina(String title, int order) {
        return TeoriaPagina.builder()
                .modulo(modulo)
                .title(title)
                .displayOrder(order)
                .build();
    }
}
