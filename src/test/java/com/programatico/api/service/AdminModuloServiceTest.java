package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.ModuloDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModuloServiceTest {

    @Mock private ModuloRepository moduloRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private ContentBlockRepository contentBlockRepository;

    @InjectMocks
    private AdminModuloService adminModuloService;

    @Test
    void listarPorTrilhaDeveRetornarModulosDaTrilha() {
        Track track = trackBase();
        Modulo modulo = moduloBase(track, ModuleType.STUDY);

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(track)).thenReturn(List.of(modulo));
        when(contentBlockRepository.countByModulo(modulo)).thenReturn(4L);

        List<ModuloDto.Response> response = adminModuloService.listarPorTrilha(1L);

        assertEquals(1, response.size());
        assertEquals("Módulo Teste", response.get(0).getTitle());
    }

    @Test
    void listarPorTrilhaDeveLancarExcecaoQuandoTrilhaNaoExiste() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminModuloService.listarPorTrilha(99L));
    }

    @Test
    void criarDevePersistirModuloComOrdemCalculada() {
        Track track = trackBase();
        ModuloDto.Request request = ModuloDto.Request.builder()
                .title("Novo Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .description("Descrição")
                .build();
        Modulo salvo = Modulo.builder()
                .id(1L)
                .track(track)
                .title("Novo Módulo")
                .moduleType(ModuleType.ACTIVITY)
                .displayOrder(2)
                .description("Descrição")
                .build();

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(moduloRepository.countByTrack(track)).thenReturn(1L);
        when(moduloRepository.save(any(Modulo.class))).thenReturn(salvo);

        adminModuloService.criar(1L, request);

        ArgumentCaptor<Modulo> captor = ArgumentCaptor.forClass(Modulo.class);
        verify(moduloRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getDisplayOrder());
        assertEquals(ModuleType.ACTIVITY, captor.getValue().getModuleType());
    }

    @Test
    void criarDeveLancarExcecaoQuandoTrilhaNaoExiste() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminModuloService.criar(99L, ModuloDto.Request.builder()
                        .title("M").moduleType(ModuleType.STUDY).build()));
    }

    @Test
    void atualizarDeveAlterarCamposDoModulo() {
        Track track = trackBase();
        Modulo modulo = moduloBase(track, ModuleType.STUDY);
        ModuloDto.Request request = ModuloDto.Request.builder()
                .title("Atualizado")
                .moduleType(ModuleType.ACTIVITY)
                .description("Nova desc")
                .build();

        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(moduloRepository.save(any(Modulo.class))).thenAnswer(i -> i.getArgument(0));
        when(exerciseRepository.countByModulo(any())).thenReturn(0L);
        when(exerciseRepository.sumXpByModulo(any())).thenReturn(0L);

        ModuloDto.Response response = adminModuloService.atualizar(1L, request);

        assertEquals("Atualizado", response.getTitle());
        assertEquals(ModuleType.ACTIVITY, response.getModuleType());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminModuloService.atualizar(99L, ModuloDto.Request.builder()
                        .title("X").moduleType(ModuleType.STUDY).build()));
    }

    @Test
    void deletarDeveChamarDeletePorIdQuandoModuloExiste() {
        when(moduloRepository.existsById(1L)).thenReturn(true);

        adminModuloService.deletar(1L);

        verify(moduloRepository).deleteById(1L);
    }

    @Test
    void deletarDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> adminModuloService.deletar(99L));
        verify(moduloRepository, never()).deleteById(any());
    }

    @Test
    void reordenarDeveAtualizarDisplayOrderDosModulos() {
        Track track = trackBase();
        Modulo mod1 = moduloBase(track, ModuleType.STUDY);
        mod1.setId(1L);
        mod1.setDisplayOrder(2);
        Modulo mod2 = moduloBase(track, ModuleType.ACTIVITY);
        mod2.setId(2L);
        mod2.setDisplayOrder(1);

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(moduloRepository.findByTrackOrderByDisplayOrderAsc(track)).thenReturn(List.of(mod1, mod2));

        adminModuloService.reordenar(1L, List.of(1L, 2L));

        assertEquals(1, mod1.getDisplayOrder());
        assertEquals(2, mod2.getDisplayOrder());
        verify(moduloRepository).saveAll(any());
    }

    private Track trackBase() {
        return Track.builder().id(1L).title("Lógica Básica")
                .description("Desc").displayOrder(1).build();
    }

    private Modulo moduloBase(Track track, ModuleType tipo) {
        return Modulo.builder().id(1L).track(track).title("Módulo Teste")
                .moduleType(tipo).displayOrder(1).description("Desc").build();
    }
}
