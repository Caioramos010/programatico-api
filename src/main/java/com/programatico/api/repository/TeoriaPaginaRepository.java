package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeoriaPaginaRepository extends JpaRepository<TeoriaPagina, Long> {

    List<TeoriaPagina> findByModuloOrderByDisplayOrderAsc(Modulo modulo);

    long countByModulo(Modulo modulo);
}
