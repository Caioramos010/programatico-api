package com.programatico.api.domain;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserSkillPerformanceId implements Serializable {
    private Long usuario;
    private Long skill;
}
