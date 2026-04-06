package com.programatico.api.repository;

import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    List<Modulo> findByTrackOrderByDisplayOrderAsc(Track track);

    long countByTrack(Track track);
}
