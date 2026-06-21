package com.programatico.api.service;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.dto.TeoriaPaginaDto;
import com.programatico.api.exception.ResourceNotFoundException;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
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
class AdminTeoriaPaginaServiceTest {

    @Mock private TeoriaPaginaRepository paginaRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private ContentBlockRepository contentBlockRepository;

    @InjectMocks
    private AdminTeoriaPaginaService adminTeoriaPaginaService;

    @Test
    void listarDeveRetornarPaginasDoModuloComTotalDeBlocos() {
        Modulo modulo = moduloBase();
        TeoriaPagina pagina = paginaBase(modulo);

        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(paginaRepository.findByModuloOrderByDisplayOrderAsc(modulo)).thenReturn(List.of(pagina));
        when(contentBlockRepository.countByPagina(pagina)).thenReturn(7L);

        List<TeoriaPaginaDto.Response> response = adminTeoriaPaginaService.listar(1L);

        assertEquals(1, response.size());
        assertEquals("Introdução", response.get(0).getTitle());
        assertEquals(7L, response.get(0).getTotalBlocos());
    }

    @Test
    void listarDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminTeoriaPaginaService.listar(99L));
    }

    @Test
    void criarDevePersistirPaginaComOrdemCalculada() {
        Modulo modulo = moduloBase();
        TeoriaPaginaDto.Request request = TeoriaPaginaDto.Request.builder()
                .title("Nova Página")
                .description("Conteúdo")
                .build();
        TeoriaPagina salva = TeoriaPagina.builder()
                .id(1L)
                .modulo(modulo)
                .title("Nova Página")
                .description("Conteúdo")
                .displayOrder(3)
                .build();

        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(paginaRepository.countByModulo(modulo)).thenReturn(2L);
        when(paginaRepository.save(any(TeoriaPagina.class))).thenReturn(salva);

        adminTeoriaPaginaService.criar(1L, request);

        ArgumentCaptor<TeoriaPagina> captor = ArgumentCaptor.forClass(TeoriaPagina.class);
        verify(paginaRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getDisplayOrder());
        assertEquals("Nova Página", captor.getValue().getTitle());
        assertEquals(modulo, captor.getValue().getModulo());
    }

    @Test
    void criarDeveLancarExcecaoQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminTeoriaPaginaService.criar(99L, TeoriaPaginaDto.Request.builder()
                        .title("X").description("Y").build()));
    }

    @Test
    void atualizarDeveAlterarCamposDaPagina() {
        Modulo modulo = moduloBase();
        TeoriaPagina pagina = paginaBase(modulo);
        TeoriaPaginaDto.Request request = TeoriaPaginaDto.Request.builder()
                .title("Atualizada")
                .description("Nova descrição")
                .build();

        when(paginaRepository.findById(1L)).thenReturn(Optional.of(pagina));
        when(paginaRepository.save(any(TeoriaPagina.class))).thenAnswer(i -> i.getArgument(0));
        when(contentBlockRepository.countByPagina(any())).thenReturn(0L);

        TeoriaPaginaDto.Response response = adminTeoriaPaginaService.atualizar(1L, request);

        assertEquals("Atualizada", response.getTitle());
        assertEquals("Nova descrição", response.getDescription());
    }

    @Test
    void atualizarDeveLancarExcecaoQuandoPaginaNaoExiste() {
        when(paginaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminTeoriaPaginaService.atualizar(99L, TeoriaPaginaDto.Request.builder()
                        .title("X").description("Y").build()));
    }

    @Test
    void deletarDeveChamarDeletePorIdQuandoPaginaExiste() {
        when(paginaRepository.existsById(1L)).thenReturn(true);

        adminTeoriaPaginaService.deletar(1L);

        verify(paginaRepository).deleteById(1L);
    }

    @Test
    void deletarDeveLancarExcecaoQuandoPaginaNaoExiste() {
        when(paginaRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> adminTeoriaPaginaService.deletar(99L));
        verify(paginaRepository, never()).deleteById(any());
    }

    private Modulo moduloBase() {
        return Modulo.builder().id(1L).title("Módulo Teste").displayOrder(1).build();
    }

    private TeoriaPagina paginaBase(Modulo modulo) {
        return TeoriaPagina.builder()
                .id(1L)
                .modulo(modulo)
                .title("Introdução")
                .description("Texto introdutório")
                .displayOrder(1)
                .build();
    }
}
