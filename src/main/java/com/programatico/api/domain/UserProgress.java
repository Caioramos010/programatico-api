package com.programatico.api.domain;

import com.programatico.api.domain.enums.ProgressStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false, unique = true)
    private Modulo modulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProgressStatus status;

    private LocalDateTime completedAt;
}
