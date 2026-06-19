package com.programatico.api.repository;

import com.programatico.api.domain.UserDailyMission;
import com.programatico.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserDailyMissionRepository extends JpaRepository<UserDailyMission, Long> {

    List<UserDailyMission> findByUsuarioAndMissionDate(Usuario usuario, LocalDate missionDate);
}
