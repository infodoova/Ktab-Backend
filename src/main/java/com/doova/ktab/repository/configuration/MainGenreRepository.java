package com.doova.ktab.repository.configuration;

import com.doova.ktab.model.configuration.MainGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MainGenreRepository extends JpaRepository<MainGenre, Long> {
}
