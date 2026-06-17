package com.programatico.api.repository;

import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserMissionRepository extends JpaRepository<UserMission, String> {

    List<UserMission> findByUsuario(Usuario usuario);

    @Modifying
    @Query("delete from UserMission um where um.usuario.id = :userId")
    void deleteByUsuarioId(@Param("userId") Long userId);
}
