package com.programatico.api.repository;

import com.programatico.api.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findFirstByOrderByDisplayOrderAsc();
}
