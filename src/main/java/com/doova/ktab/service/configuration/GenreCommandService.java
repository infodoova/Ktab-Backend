package com.doova.ktab.service.configuration;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.mappers.configuration.GenreMapper;
import com.doova.ktab.model.configuration.MainGenre;
import com.doova.ktab.model.configuration.SubGenre;
import com.doova.ktab.repository.configuration.MainGenreRepository;
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
public class GenreCommandService {

    private final MainGenreRepository mainRepo;
    private final GenreMapper genreMapper;

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
