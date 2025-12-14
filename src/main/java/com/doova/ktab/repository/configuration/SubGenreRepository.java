package com.doova.ktab.repository.configuration;

import com.doova.ktab.model.configuration.SubGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubGenreRepository extends JpaRepository<SubGenre, Long> {
    List<SubGenre> findAllByMainGenreId(Long mainGenreId);
}
