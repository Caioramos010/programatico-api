package com.programatico.api.repository;

import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    long countByDataCriacaoAfter(Instant data);

    List<Usuario> findBySubscriptionTypeAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(
            SubscriptionType subscriptionType,
            Instant subscriptionExpiresAt
    );

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByEmailOrUsername(String email, String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<Usuario> findByCodigoAtivacao(String codigo);

    Optional<Usuario> findByCodigoRedefinicaoSenha(String codigo);
}
