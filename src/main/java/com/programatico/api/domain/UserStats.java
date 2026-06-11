package com.programatico.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Usuario usuario;

    private Integer totalXp;

    private Integer currentLives;

    /** Marco da recarga: instante a partir do qual a próxima vida é contada. Nulo quando as vidas estão cheias. */
    @Column(name = "lives_updated_at")
    private LocalDateTime livesUpdatedAt;

    private Integer currentStreak;

    private Integer highestStreak;

    private LocalDateTime lastActivityDate;
}
