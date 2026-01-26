package com.doova.ktab.service.configuration;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.mappers.configuration.GenreMapper;
import com.doova.ktab.model.configuration.MainGenre;
import com.doova.ktab.repository.configuration.MainGenreRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenreQueryService {

    private final MainGenreRepository mainRepo;
    private final GenreMapper genreMapper;

    @Transactional(readOnly = true)
    public List<MainGenreDTO> getAllGenres() {
        return mainRepo.findAll().stream().map(genreMapper::toMainGenreDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MainGenreDTO getById(Long id) {
        MainGenre genre = mainRepo.findById(id).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.GENRE_NOT_FOUND.getKey()));

        return genreMapper.toMainGenreDTO(genre);
    }
}
