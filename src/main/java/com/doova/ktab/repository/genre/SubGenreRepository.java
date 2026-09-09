package com.doova.ktab.repository.genre;

import com.doova.ktab.model.genre.SubGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubGenreRepository extends JpaRepository<SubGenre, Long> {
    List<SubGenre> findAllByMainGenreId(Long mainGenreId);
}
