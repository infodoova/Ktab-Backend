package com.doova.ktab.service.genre.impl;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.mappers.genre.GenreMapper;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.repository.genre.MainGenreRepository;
import com.doova.ktab.service.genre.GenreQueryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenreQueryServiceImpl implements GenreQueryService {

    private final MainGenreRepository mainRepo;
    private final GenreMapper genreMapper;

    @Override
    @Transactional(readOnly = true)
    public List<MainGenreDTO> getAllGenres() {
        return mainRepo.findAll().stream().map(genreMapper::toMainGenreDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MainGenreDTO getById(Long id) {
        MainGenre genre = mainRepo.findById(id).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.GENRE_NOT_FOUND.getKey()));
        return genreMapper.toMainGenreDTO(genre);
    }
}
