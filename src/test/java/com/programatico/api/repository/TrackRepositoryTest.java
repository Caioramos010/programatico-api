package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TrackRepositoryTest {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ModuloRepository moduloRepository;

    @Test
    void deveEncontrarPrimeiraTrilhaPorDisplayOrder() {
        Track t1 = trackRepository.save(Track.builder()
                .title("Segunda").description("Desc").displayOrder(2).icon(null).build());
        Track t2 = trackRepository.save(Track.builder()
                .title("Primeira").description("Desc").displayOrder(1).icon(null).build());

        Optional<Track> first = trackRepository.findFirstByOrderByDisplayOrderAsc();

        assertTrue(first.isPresent());
        assertEquals("Primeira", first.get().getTitle());
    }

    @Test
    void deveRetornarVazioQuandoNenhumaTrilhaCadastrada() {
        Optional<Track> result = trackRepository.findFirstByOrderByDisplayOrderAsc();
        assertFalse(result.isPresent());
    }

    @Test
    void deveContarModulosDaTrilha() {
        Track track = trackRepository.save(Track.builder()
                .title("Trilha").description("Desc").displayOrder(1).icon(null).build());
        moduloRepository.save(Modulo.builder()
                .track(track).title("Mod 1").moduleType(ModuleType.STUDY).displayOrder(1).build());
        moduloRepository.save(Modulo.builder()
                .track(track).title("Mod 2").moduleType(ModuleType.ACTIVITY).displayOrder(2).build());

        long count = moduloRepository.countByTrack(track);

        assertEquals(2L, count);
    }

    @Test
    void deveRetornarModulosOrdenadosPorDisplayOrder() {
        Track track = trackRepository.save(Track.builder()
                .title("Trilha").description("Desc").displayOrder(1).icon(null).build());
        moduloRepository.save(Modulo.builder()
                .track(track).title("Terceiro").moduleType(ModuleType.STUDY).displayOrder(3).build());
        moduloRepository.save(Modulo.builder()
                .track(track).title("Primeiro").moduleType(ModuleType.STUDY).displayOrder(1).build());
        moduloRepository.save(Modulo.builder()
                .track(track).title("Segundo").moduleType(ModuleType.ACTIVITY).displayOrder(2).build());

        List<Modulo> modulos = moduloRepository.findByTrackOrderByDisplayOrderAsc(track);

        assertEquals(3, modulos.size());
        assertEquals("Primeiro", modulos.get(0).getTitle());
        assertEquals("Segundo", modulos.get(1).getTitle());
        assertEquals("Terceiro", modulos.get(2).getTitle());
    }
}
