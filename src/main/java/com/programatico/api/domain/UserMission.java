package com.programatico.api.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_missions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "mission_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMission {

    @Id
    @Column(length = 45)
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    private Integer currentProgress;

    private Boolean isCompleted;

    private LocalDateTime completedAt;
}
