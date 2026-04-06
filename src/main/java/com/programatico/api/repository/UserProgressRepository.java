package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.UserProgress;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    List<UserProgress> findByUsuarioAndModuloIn(Usuario usuario, List<Modulo> modulos);
}
