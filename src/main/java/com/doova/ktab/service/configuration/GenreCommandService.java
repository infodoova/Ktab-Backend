package com.doova.ktab.service.configuration;

import com.doova.ktab.repository.configuration.MainGenreRepository;
import com.doova.ktab.repository.configuration.SubGenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenreCommandService  {

    private final MainGenreRepository mainRepo;
    private final SubGenreRepository subRepo;
}
