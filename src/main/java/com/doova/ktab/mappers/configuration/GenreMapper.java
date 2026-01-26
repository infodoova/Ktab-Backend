package com.doova.ktab.mappers.configuration;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.model.configuration.MainGenre;
import com.doova.ktab.model.configuration.SubGenre;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GenreMapper {

    // Convert MainGenre to MainGenreDTO
    MainGenreDTO toMainGenreDTO(MainGenre mainGenre);

    // Convert SubGenre to SubGenreDTO
    SubGenreDTO toSubGenreDTO(SubGenre subGenre);

    // Convert MainGenreDTO to MainGenre (for writing to DB)
    MainGenre toMainGenre(MainGenreDTO mainGenreDTO);

    // Convert SubGenreDTO to SubGenre (for writing to DB)
    SubGenre toSubGenre(SubGenreDTO subGenreDTO);
}
