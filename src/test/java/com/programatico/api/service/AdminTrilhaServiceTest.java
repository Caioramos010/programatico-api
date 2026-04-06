package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.dto.TrilhaDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTrilhaServiceTest {

    @Mock private TrackRepository trackRepository;
    @Mock private ModuloRepository moduloRepository;

    @InjectMocks
    private AdminTrilhaService adminTrilhaService;

    @Test
    void listarTodasDeveRetornarListaDeTrilhas() {
        Track track = trackBase();
        when(trackRepository.findAll()).thenReturn(List.of(track));
        when(moduloRepository.countByTrack(track)).thenReturn(3L);

        List<TrilhaDto.Response> response = adminTrilhaService.listarTodas();

        assertEquals(1, response.size());
        assertEquals("Lógica Básica", response.get(0).getTitle());
        assertEquals(3L, response.get(0).getTotalModulos());
    }

    @Test
    void criarDevePersistirTrilhaComOrdemCalculada() {
        TrilhaDto.Request request = TrilhaDto.Request.builder()
                .title("Nova Trilha")
                .description("Descrição")
                .build();
        Track salva = Track.builder().id(1L).title("Nova Trilha")
                .description("Descrição").displayOrder(2).build();

        when(trackRepository.count()).thenReturn(1L);
        when(trackRepository.save(any(Track.class))).thenReturn(salva);

        TrilhaDto.Response response = adminTrilhaService.criar(request);

        ArgumentCaptor<Track> captor = ArgumentCaptor.forClass(Track.class);
        verify(trackRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getDisplayOrder());
        assertNotNull(response);
        assertEquals("Nova Trilha", response.getTitle());
    }

    @Test
    void atualizarDeveAlterarCamposDaTrilha() {
        Track track = trackBase();
        TrilhaDto.Request request = TrilhaDto.Request.builder()
                .title("Atualizada")
                .description("Nova descrição")
                .build();

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(i -> i.getArgument(0));
        when(moduloRepository.countByTrack(any())).thenReturn(0L);

        TrilhaDto.Response response = adminTrilhaService.atualizar(1L, request);

        assertEquals("Atualizada", response.getTitle());
        assertEquals("Nova descrição", response.getDescription());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoTrilhaNaoExiste() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminTrilhaService.atualizar(99L, TrilhaDto.Request.builder()
                        .title("X").description("Y").build()));
    }

    @Test
    void deletarDeveChamarDeletePorIdQuandoTrilhaExiste() {
        when(trackRepository.existsById(1L)).thenReturn(true);

        adminTrilhaService.deletar(1L);

        verify(trackRepository).deleteById(1L);
    }

    @Test
    void deletarDeveLancarExcecaoQuandoTrilhaNaoExiste() {
        when(trackRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> adminTrilhaService.deletar(99L));
        verify(trackRepository, never()).deleteById(any());
    }

    private Track trackBase() {
        return Track.builder()
                .id(1L)
                .title("Lógica Básica")
                .description("Introdução à lógica")
                .displayOrder(1)
                .build();
    }
}
