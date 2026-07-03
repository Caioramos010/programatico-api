package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ModuloRepositoryTest {

    @Autowired private ModuloRepository moduloRepository;
    @Autowired private TrackRepository trackRepository;

    private Track track;

    @BeforeEach
    void setUp() {
        track = trackRepository.save(Track.builder()
                .title("Trilha mod")
                .description("Desc")
                .displayOrder(1)
                .build());
    }

    @Test
    void findByTrackOrderByDisplayOrderAsc() {
        moduloRepository.save(modulo("Terceiro", 3));
        moduloRepository.save(modulo("Primeiro", 1));
        moduloRepository.save(modulo("Segundo", 2));

        List<Modulo> modulos = moduloRepository.findByTrackOrderByDisplayOrderAsc(track);

        assertEquals(3, modulos.size());
        assertEquals("Primeiro", modulos.get(0).getTitle());
        assertEquals("Segundo", modulos.get(1).getTitle());
        assertEquals("Terceiro", modulos.get(2).getTitle());
    }

    @Test
    void countByTrack() {
        moduloRepository.save(modulo("A", 1));
        moduloRepository.save(modulo("B", 2));

        assertEquals(2L, moduloRepository.countByTrack(track));
    }

    private Modulo modulo(String title, int order) {
        return Modulo.builder()
                .track(track)
                .title(title)
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(order)
                .build();
    }
}
