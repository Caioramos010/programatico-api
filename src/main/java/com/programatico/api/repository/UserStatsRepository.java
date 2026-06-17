package com.programatico.api.repository;

import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    Optional<UserStats> findByUsuario(Usuario usuario);

    @Modifying
    @Query("delete from UserStats us where us.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
