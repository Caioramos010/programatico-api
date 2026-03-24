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

    private Integer currentStreak;

    private Integer highestStreak;

    private LocalDateTime lastActivityDate;
}
