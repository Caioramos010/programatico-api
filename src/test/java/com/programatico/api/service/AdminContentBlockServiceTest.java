package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.dto.ContentBlockDto;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentBlockServiceTest {

    @Mock private ContentBlockRepository contentBlockRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private TeoriaPaginaRepository paginaRepository;

    @InjectMocks
    private AdminContentBlockService service;

    private Modulo modulo() {
        return Modulo.builder().id(1L).title("Módulo").build();
    }

    private TeoriaPagina pagina() {
        return TeoriaPagina.builder().id(10L).modulo(modulo()).title("Página").build();
    }

    private ContentBlock block(Long id) {
        return ContentBlock.builder()
                .id(id)
                .modulo(modulo())
                .layoutType(LayoutType.TEXT)
                .textContent("conteúdo")
                .displayOrder(0)
                .build();
    }

    private ContentBlockDto.Request request() {
        return ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT)
                .textContent("novo conteúdo")
                .displayOrder(2)
                .build();
    }

    // ── listar ────────────────────────────────────────────────────────

    @Test
    void listarPorPaginaRetornaBlocosMapeados() {
        TeoriaPagina pagina = pagina();
        when(paginaRepository.findById(10L)).thenReturn(Optional.of(pagina));
        when(contentBlockRepository.findByPaginaOrderByDisplayOrderAsc(pagina))
                .thenReturn(List.of(block(1L)));

        List<ContentBlockDto.Response> resp = service.listarPorPagina(10L);

        assertEquals(1, resp.size());
        assertEquals(LayoutType.TEXT, resp.get(0).getLayoutType());
    }

    @Test
    void listarPorPaginaLancaQuandoPaginaNaoExiste() {
        when(paginaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listarPorPagina(99L));
    }

    @Test
    void listarPorModuloLancaQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listarPorModulo(99L));
    }

    // ── criar ─────────────────────────────────────────────────────────

    @Test
    void criarParaPaginaUsaModuloDaPaginaESalva() {
        TeoriaPagina pagina = pagina();
        when(paginaRepository.findById(10L)).thenReturn(Optional.of(pagina));
        when(contentBlockRepository.save(any(ContentBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        ContentBlockDto.Response resp = service.criarParaPagina(10L, request());

        assertEquals(1L, resp.getModuloId());
        assertEquals("novo conteúdo", resp.getTextContent());
        verify(contentBlockRepository).save(any(ContentBlock.class));
    }

    @Test
    void criarLancaQuandoModuloNaoExiste() {
        when(moduloRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.criar(99L, request()));
        verify(contentBlockRepository, never()).save(any(ContentBlock.class));
    }

    // ── atualizar ─────────────────────────────────────────────────────

    @Test
    void atualizarModificaCamposESalva() {
        when(contentBlockRepository.findById(1L)).thenReturn(Optional.of(block(1L)));
        when(contentBlockRepository.save(any(ContentBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        ContentBlockDto.Response resp = service.atualizar(1L, request());

        assertEquals("novo conteúdo", resp.getTextContent());
        assertEquals(2, resp.getDisplayOrder());
    }

    @Test
    void atualizarLancaQuandoBlocoNaoExiste() {
        when(contentBlockRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.atualizar(99L, request()));
    }

    // ── deletar ───────────────────────────────────────────────────────

    @Test
    void deletarRemoveQuandoExiste() {
        when(contentBlockRepository.existsById(1L)).thenReturn(true);

        service.deletar(1L);

        verify(contentBlockRepository).deleteById(1L);
    }

    @Test
    void deletarLancaQuandoNaoExiste() {
        when(contentBlockRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deletar(99L));
        verify(contentBlockRepository, never()).deleteById(any());
    }
}
