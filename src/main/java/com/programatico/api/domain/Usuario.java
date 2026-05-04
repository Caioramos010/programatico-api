package com.programatico.api.domain;

import com.programatico.api.domain.enums.NivelHabilidade;
import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 2, max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank
    @Column(name = "password", nullable = false, length = 255)
    private String senha;

    @Min(value = 12, message = "Idade mínima é 12 anos")
    @Max(120)
    @Column(name = "age", nullable = true)
    private Integer idade;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean ativo = false;

    @Column(name = "activation_code", length = 20)
    private String codigoAtivacao;

    @Column(name = "password_reset_code", length = 20)
    private String codigoRedefinicaoSenha;

    @Column(name = "password_reset_code_expires_at")
    private Instant dataExpiracaoCodigoRedefinicao;

    @Column(name = "account_deletion_code", length = 20)
    private String codigoExclusaoConta;

    @Column(name = "account_deletion_code_expires_at")
    private Instant dataExpiracaoCodigoExclusao;

    @Column(name = "login_verification_code", length = 20)
    private String codigoVerificacaoLogin;

    @Column(name = "login_verification_code_expires_at")
    private Instant dataExpiracaoCodigoLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "updated_at")
    private Instant dataAtualizacao;

    @Column(columnDefinition = "LONGTEXT")
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", length = 20)
    private NivelHabilidade nivelHabilidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SubscriptionType subscriptionType = SubscriptionType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private TipoUsuario role = TipoUsuario.USER;

    @PrePersist
    protected void onCreate() {
        dataCriacao = Instant.now();
        dataAtualizacao = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = Instant.now();
    }
}
