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

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private SessionType sessionType;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer timeLimitSeconds;
}
