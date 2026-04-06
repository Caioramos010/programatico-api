package com.programatico.api.repository;

import com.programatico.api.domain.UserMission;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMissionRepository extends JpaRepository<UserMission, String> {

    List<UserMission> findByUsuario(Usuario usuario);
}
