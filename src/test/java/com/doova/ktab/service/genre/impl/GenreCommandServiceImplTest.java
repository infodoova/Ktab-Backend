package com.doova.ktab.service.genre.impl;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.mappers.genre.GenreMapper;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import com.doova.ktab.repository.genre.MainGenreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenreCommandServiceImplTest {

    @Mock
    private MainGenreRepository mainRepo;

    @Mock
    private GenreMapper genreMapper;

    @InjectMocks
    private GenreCommandServiceImpl commandService;

    private MainGenre existingGenre;

    @BeforeEach
    void setUp() {
        existingGenre = new MainGenre();
        existingGenre.setId(1L);
        existingGenre.setNameAr("خيال");
        existingGenre.setNameEn("Fiction");
        existingGenre.setDescription("Fiction genre");
        existingGenre.setSubGenres(new ArrayList<>());

        SubGenre sub = new SubGenre();
        sub.setId(101L);
        sub.setNameAr("خيال علمي");
        sub.setNameEn("Science Fiction");
        sub.setMainGenre(existingGenre);
        existingGenre.getSubGenres().add(sub);
    }

    @Test
    @DisplayName("save_unifiedNamePayload_updatesNameArAndMaintainsCompatibility")
    void save_unifiedNamePayload_updatesNameArAndMaintainsCompatibility() {
        MainGenreDTO requestDto = new MainGenreDTO();
        requestDto.setId(1L);
        requestDto.setName("أدب وروايات");
        requestDto.setSubGenres(List.of(new SubGenreDTO(101L, "خيال علمي حديث")));

        when(mainRepo.findById(1L)).thenReturn(Optional.of(existingGenre));
        when(mainRepo.save(any(MainGenre.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(genreMapper.toMainGenreDTO(any(MainGenre.class))).thenAnswer(invocation -> {
            MainGenre saved = invocation.getArgument(0);
            return new MainGenreDTO(saved.getId(), saved.getNameAr(), List.of(new SubGenreDTO(101L, "خيال علمي حديث")));
        });

        MainGenreDTO result = commandService.save(requestDto);

        assertThat(result).isNotNull();
        assertThat(existingGenre.getNameAr()).isEqualTo("أدب وروايات");
        assertThat(existingGenre.getNameEn()).isEqualTo("Fiction"); // Preserved legacy English name
        assertThat(existingGenre.getSubGenres().get(0).getNameAr()).isEqualTo("خيال علمي حديث");
        verify(mainRepo).save(existingGenre);
    }

    @Test
    @DisplayName("save_legacyPayloadWithNameArAndNameEn_preservesBothFields")
    void save_legacyPayloadWithNameArAndNameEn_preservesBothFields() {
        MainGenreDTO requestDto = new MainGenreDTO();
        requestDto.setId(1L);
        requestDto.setNameAr("خيال مطور");
        requestDto.setNameEn("Advanced Fiction");
        requestDto.setDescription("Updated description");

        when(mainRepo.findById(1L)).thenReturn(Optional.of(existingGenre));
        when(mainRepo.save(any(MainGenre.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(genreMapper.toMainGenreDTO(any(MainGenre.class))).thenReturn(requestDto);

        MainGenreDTO result = commandService.save(requestDto);

        assertThat(result).isNotNull();
        assertThat(existingGenre.getNameAr()).isEqualTo("خيال مطور");
        assertThat(existingGenre.getNameEn()).isEqualTo("Advanced Fiction");
        assertThat(existingGenre.getDescription()).isEqualTo("Updated description");
        verify(mainRepo).save(existingGenre);
    }
}
