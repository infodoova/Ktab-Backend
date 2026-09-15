package com.doova.ktab.service.genre;

import com.doova.ktab.dto.genre.MainGenreDTO;

import java.util.List;

public interface GenreQueryService {

    List<MainGenreDTO> getAllGenres();

    List<MainGenreDTO> getAllGenres(boolean full);

    MainGenreDTO getById(Long id);

    MainGenreDTO getById(Long id, boolean full);
}
