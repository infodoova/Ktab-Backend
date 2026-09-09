package com.doova.ktab.service.genre.impl;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.mappers.genre.GenreMapper;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import com.doova.ktab.repository.genre.MainGenreRepository;
import com.doova.ktab.service.genre.GenreCommandService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenreCommandServiceImpl implements GenreCommandService {

    private final MainGenreRepository mainRepo;
    private final GenreMapper genreMapper;

    @Override
    @Transactional
    public MainGenreDTO save(MainGenreDTO dto) {
        MainGenre mainGenre = dto.getId() != null ? mainRepo.findById(dto.getId()).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.GENRE_NOT_FOUND.getKey())) : new MainGenre();

        mainGenre.setNameEn(dto.getNameEn());
        mainGenre.setNameAr(dto.getNameAr());
        mainGenre.setDescription(dto.getDescription());

        syncSubGenres(mainGenre, dto.getSubGenres());

        return genreMapper.toMainGenreDTO(mainRepo.save(mainGenre));
    }

    private void syncSubGenres(MainGenre mainGenre, List<SubGenreDTO> subGenreDTOs) {
        if (subGenreDTOs == null || subGenreDTOs.isEmpty()) {
            mainGenre.clearSubGenres();
            return;
        }

        Map<Long, SubGenre> existing = mainGenre.getSubGenres().stream().collect(Collectors.toMap(SubGenre::getId, Function.identity()));

        for (SubGenreDTO dto : subGenreDTOs) {
            SubGenre subGenre = dto.getId() != null && existing.containsKey(dto.getId()) ? existing.get(dto.getId()) : new SubGenre();

            subGenre.setNameEn(dto.getNameEn());
            subGenre.setNameAr(dto.getNameAr());
            subGenre.setDescription(dto.getDescription());

            mainGenre.addSubGenre(subGenre);
        }
    }
}
