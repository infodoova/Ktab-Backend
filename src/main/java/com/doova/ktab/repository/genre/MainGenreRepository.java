package com.doova.ktab.repository.genre;

import com.doova.ktab.model.genre.MainGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MainGenreRepository extends JpaRepository<MainGenre, Long> {
}
