package com.programatico.api.repository;

import com.programatico.api.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUsuarioId(Long usuarioId);
}
