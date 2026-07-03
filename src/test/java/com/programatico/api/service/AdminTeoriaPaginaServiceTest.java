package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.dto.TeoriaPaginaDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTeoriaPaginaServiceTest {

    @Mock private TeoriaPaginaRepository paginaRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private ContentBlockRepository contentBlockRepository;
    @InjectMocks private AdminTeoriaPaginaService adminTeoriaPaginaService;

    @Test
    void listarDeveRetornarPaginasComContagemDeBlocos() {
        Modulo modulo = moduloBase();
        TeoriaPagina pagina = TeoriaPagina.builder().id(1L).modulo(modulo).title("P1")
                .description("D").displayOrder(1).build();
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(paginaRepository.findByModuloOrderByDisplayOrderAsc(modulo)).thenReturn(List.of(pagina));
        when(contentBlockRepository.countByPagina(pagina)).thenReturn(2L);

        List<TeoriaPaginaDto.Response> response = adminTeoriaPaginaService.listar(1L);

        assertEquals(1, response.size());
        assertEquals(2L, response.get(0).getTotalBlocos());
    }

    @Test
    void criarDeveCalcularOrdemAutomaticamente() {
        Modulo modulo = moduloBase();
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(paginaRepository.countByModulo(modulo)).thenReturn(1L);
        when(paginaRepository.save(any(TeoriaPagina.class))).thenAnswer(inv -> {
            TeoriaPagina p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });

        TeoriaPaginaDto.Response response = adminTeoriaPaginaService.criar(1L,
                TeoriaPaginaDto.Request.builder().title("Nova").description("Desc").build());

        assertEquals("Nova", response.getTitle());
        assertEquals(2, response.getDisplayOrder());
    }

    @Test
    void deletarDeveFalharQuandoPaginaNaoExiste() {
        when(paginaRepository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> adminTeoriaPaginaService.deletar(99L));
        verify(paginaRepository).existsById(99L);
    }

    private Modulo moduloBase() {
        Track track = Track.builder().id(1L).title("T").description("D").displayOrder(1).build();
        return Modulo.builder().id(1L).track(track).title("M").moduleType(ModuleType.STUDY)
                .displayOrder(1).description("D").build();
    }
}
