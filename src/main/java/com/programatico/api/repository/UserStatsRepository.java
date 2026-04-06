package com.programatico.api.repository;

import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {

    Optional<UserStats> findByUsuario(Usuario usuario);
}
