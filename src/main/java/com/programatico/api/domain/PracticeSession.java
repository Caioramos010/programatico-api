package com.programatico.api.domain;

import com.programatico.api.domain.enums.SessionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "module_id")
    private Modulo modulo;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private SessionType sessionType;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer timeLimitSeconds;

    /**
     * Tempo efetivo de prática (segundos), somado a cada interação com teto por
     * intervalo — ausências (fechou e retomou dias depois) não contam na duração.
     */
    private Integer activeSeconds;

    /** Última interação da sessão; âncora do cálculo do tempo ativo. */
    private LocalDateTime lastInteractionAt;
}
