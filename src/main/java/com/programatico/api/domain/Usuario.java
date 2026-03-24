package com.programatico.api.domain;

import com.programatico.api.domain.enums.SubscriptionType;
import com.programatico.api.domain.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "usuarios")
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
    @Column(nullable = false, length = 255)
    private String senha;

    @Min(1)
    @Max(120)
    @Column(nullable = true)
    private Integer idade;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = false;

    @Column(length = 20)
    private String codigoAtivacao;

    @Column(length = 20)
    private String codigoRedefinicaoSenha;

    private Instant dataExpiracaoCodigoRedefinicao;

    @Column(nullable = false, updatable = false)
    private Instant dataCriacao;

    private Instant dataAtualizacao;

    @Column(length = 500)
    private String icon;

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
