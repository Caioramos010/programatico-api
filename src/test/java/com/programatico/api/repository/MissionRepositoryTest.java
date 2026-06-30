package com.programatico.api.repository;

import com.programatico.api.domain.Mission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class MissionRepositoryTest {

    @Autowired private MissionRepository missionRepository;

    @Test
    void saveEFindById() {
        Mission mission = missionRepository.save(Mission.builder()
                .title("Ganhar XP")
                .objectiveType("XP")
                .xpReward(15)
                .quantidade(3)
                .build());

        Mission found = missionRepository.findById(mission.getId()).orElseThrow();
        assertEquals("Ganhar XP", found.getTitle());
        assertEquals(3, found.getQuantidade());
        assertNotNull(found.getId());
    }
}
