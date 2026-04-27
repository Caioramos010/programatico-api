package com.programatico.api.domain;

import com.programatico.api.domain.enums.NivelHabilidade;
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

    @Min(value = 12, message = "Idade mínima é 12 anos")
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

    @Column(length = 20)
    private String codigoExclusaoConta;

    private Instant dataExpiracaoCodigoExclusao;

    @Column(nullable = false, updatable = false)
    private Instant dataCriacao;

    private Instant dataAtualizacao;

    @Column(columnDefinition = "LONGTEXT")
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
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
