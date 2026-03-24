package com.programatico.api.domain;

import com.programatico.api.domain.enums.ExerciseType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false)
    private Modulo modulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExerciseType exerciseType;

    @Column(nullable = false, columnDefinition = "json")
    private String exerciseData;

    @Column(nullable = false)
    private Integer xpReward;
}
