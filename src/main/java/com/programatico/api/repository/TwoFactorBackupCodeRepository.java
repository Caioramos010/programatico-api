package com.programatico.api.repository;

import com.programatico.api.domain.TwoFactorBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TwoFactorBackupCodeRepository extends JpaRepository<TwoFactorBackupCode, Long> {

    List<TwoFactorBackupCode> findByUsuarioIdAndUsedAtIsNull(Long usuarioId);

    long countByUsuarioIdAndUsedAtIsNull(Long usuarioId);

    @Modifying
    @Query("DELETE FROM TwoFactorBackupCode c WHERE c.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
