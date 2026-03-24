package com.programatico.api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_skill_performance")
@IdClass(UserSkillPerformanceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSkillPerformance {

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario usuario;

    @Id
    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    private Integer totalAttempts;

    private Integer correctAttempts;

    private Double successRate;
}
