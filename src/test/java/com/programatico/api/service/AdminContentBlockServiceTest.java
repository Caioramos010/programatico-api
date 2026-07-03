package com.programatico.api.service;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.domain.enums.ModuleType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentBlockServiceTest {

    @Mock private ContentBlockRepository contentBlockRepository;
    @Mock private ModuloRepository moduloRepository;
    @Mock private TeoriaPaginaRepository paginaRepository;
    @InjectMocks private AdminContentBlockService adminContentBlockService;

    @Test
    void listarPorPaginaDeveRetornarBlocos() {
        Modulo modulo = moduloBase();
        TeoriaPagina pagina = TeoriaPagina.builder().id(1L).modulo(modulo).title("P").displayOrder(1).build();
        ContentBlock block = ContentBlock.builder().id(1L).modulo(modulo).pagina(pagina)
                .layoutType(LayoutType.TEXT).textContent("Texto").displayOrder(1).build();
        when(paginaRepository.findById(1L)).thenReturn(Optional.of(pagina));
        when(contentBlockRepository.findByPaginaOrderByDisplayOrderAsc(pagina)).thenReturn(List.of(block));

        List<ContentBlockDto.Response> response = adminContentBlockService.listarPorPagina(1L);

        assertEquals(1, response.size());
        assertEquals(LayoutType.TEXT, response.get(0).getLayoutType());
    }

    @Test
    void criarParaModuloDevePersistirBloco() {
        Modulo modulo = moduloBase();
        ContentBlockDto.Request request = ContentBlockDto.Request.builder()
                .layoutType(LayoutType.TEXT)
                .textContent("Conteúdo")
                .displayOrder(1)
                .build();
        when(moduloRepository.findById(1L)).thenReturn(Optional.of(modulo));
        when(contentBlockRepository.save(any(ContentBlock.class))).thenAnswer(inv -> {
            ContentBlock b = inv.getArgument(0);
            b.setId(3L);
            return b;
        });

        ContentBlockDto.Response response = adminContentBlockService.criar(1L, request);

        assertEquals("Conteúdo", response.getTextContent());
        verify(contentBlockRepository).save(any(ContentBlock.class));
    }

    @Test
    void deletarDeveFalharQuandoBlocoNaoExiste() {
        when(contentBlockRepository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> adminContentBlockService.deletar(99L));
    }

    private Modulo moduloBase() {
        Track track = Track.builder().id(1L).title("T").description("D").displayOrder(1).build();
        return Modulo.builder().id(1L).track(track).title("M").moduleType(ModuleType.STUDY)
                .displayOrder(1).description("D").build();
    }
}
