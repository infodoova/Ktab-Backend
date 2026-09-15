package com.doova.ktab.mappers.genre;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring")
public interface GenreMapper {

    @Mapping(target = "name", expression = "java(resolveName(mainGenre))")
    @Mapping(target = "subGenres", qualifiedByName = "toFullSubGenreDTO")
    MainGenreDTO toMainGenreDTO(MainGenre mainGenre);

    @Named("toFullSubGenreDTO")
    @Mapping(target = "name", expression = "java(resolveName(subGenre))")
    SubGenreDTO toSubGenreDTO(SubGenre subGenre);

    default MainGenreDTO toSimpleMainGenreDTO(MainGenre mainGenre) {
        if (mainGenre == null) return null;
        List<SubGenreDTO> subDtos = mainGenre.getSubGenres() != null
                ? mainGenre.getSubGenres().stream().map(this::toSimpleSubGenreDTO).toList()
                : Collections.emptyList();
        return new MainGenreDTO(mainGenre.getId(), resolveName(mainGenre), subDtos);
    }

    default SubGenreDTO toSimpleSubGenreDTO(SubGenre subGenre) {
        if (subGenre == null) return null;
        return new SubGenreDTO(subGenre.getId(), resolveName(subGenre));
    }

    MainGenre toMainGenre(MainGenreDTO mainGenreDTO);

    SubGenre toSubGenre(SubGenreDTO subGenreDTO);

    default String resolveName(MainGenre mainGenre) {
        if (mainGenre == null) return null;
        return (mainGenre.getNameAr() != null && !mainGenre.getNameAr().isBlank())
                ? mainGenre.getNameAr() : mainGenre.getNameEn();
    }

    default String resolveName(SubGenre subGenre) {
        if (subGenre == null) return null;
        return (subGenre.getNameAr() != null && !subGenre.getNameAr().isBlank())
                ? subGenre.getNameAr() : subGenre.getNameEn();
    }
}
