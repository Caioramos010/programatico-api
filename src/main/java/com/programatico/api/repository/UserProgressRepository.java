package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    List<UserProgress> findByUsuarioAndModuloIn(Usuario usuario, List<Modulo> modulos);

    Optional<UserProgress> findByUsuarioAndModulo(Usuario usuario, Modulo modulo);

    @Modifying
    @Query("delete from UserProgress up where up.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
