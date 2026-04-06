package com.programatico.api.repository;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {

    List<ContentBlock> findByModuloOrderByDisplayOrderAsc(Modulo modulo);

    long countByModulo(Modulo modulo);

    List<ContentBlock> findByPaginaOrderByDisplayOrderAsc(TeoriaPagina pagina);

    long countByPagina(TeoriaPagina pagina);
}
