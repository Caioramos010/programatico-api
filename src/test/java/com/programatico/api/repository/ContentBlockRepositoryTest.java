package com.programatico.api.repository;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.domain.enums.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ContentBlockRepositoryTest {

    @Autowired private ContentBlockRepository repository;
    @Autowired private TeoriaPaginaRepository paginaRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private TrackRepository trackRepository;

    private Modulo modulo;
    private TeoriaPagina pagina;

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
        pagina = paginaRepository.save(TeoriaPagina.builder()
                .modulo(modulo)
                .title("Página 1")
                .displayOrder(1)
                .build());

        repository.save(block(modulo, pagina, "B2", 2));
        repository.save(block(modulo, pagina, "B1", 1));
        repository.save(block(modulo, null, "Modulo only", 1));
    }

    @Test
    void findByPaginaOrderByDisplayOrderAsc() {
        List<ContentBlock> blocos = repository.findByPaginaOrderByDisplayOrderAsc(pagina);
        assertEquals(2, blocos.size());
        assertEquals("B1", blocos.get(0).getTextContent());
    }

    @Test
    void findByModuloOrderByDisplayOrderAscECount() {
        List<ContentBlock> blocos = repository.findByModuloOrderByDisplayOrderAsc(modulo);
        assertEquals(3, blocos.size());
        assertEquals(3L, repository.countByModulo(modulo));
        assertEquals(2L, repository.countByPagina(pagina));
    }

    private ContentBlock block(Modulo mod, TeoriaPagina pg, String text, int order) {
        return ContentBlock.builder()
                .modulo(mod)
                .pagina(pg)
                .layoutType(LayoutType.TEXT)
                .textContent(text)
                .displayOrder(order)
                .build();
    }
}
