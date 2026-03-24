package com.programatico.api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exercise_skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exercise_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseSkill {

    @Id
    @Column(length = 45)
    private String id;

    @ManyToOne
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;
}
