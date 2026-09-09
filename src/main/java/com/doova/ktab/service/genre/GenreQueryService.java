package com.doova.ktab.service.genre;

import com.doova.ktab.dto.genre.MainGenreDTO;

import java.util.List;

public interface GenreQueryService {

    List<MainGenreDTO> getAllGenres();

    MainGenreDTO getById(Long id);
}
