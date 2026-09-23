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

    @Test
    @DisplayName("save_withSubGenresReconciliation_updatesExistingInsertsNewAndDeletesMissing")
    void save_withSubGenresReconciliation_updatesExistingInsertsNewAndDeletesMissing() {
        // existingGenre has subgenre 101 ("خيال علمي")
        // Add a second existing subgenre 102 ("فانتازيا")
        SubGenre sub2 = new SubGenre();
        sub2.setId(102L);
        sub2.setNameAr("فانتازيا");
        sub2.setNameEn("Fantasy");
        sub2.setMainGenre(existingGenre);
        existingGenre.getSubGenres().add(sub2);

        // Request DTO:
        // - Keeps 101 with updated name (UPDATE/MERGE)
        // - Omits 102 (ORPHAN REMOVAL/DELETE)
        // - Adds new subgenre with id=null (INSERT/PERSIST)
        SubGenreDTO updated101 = new SubGenreDTO();
        updated101.setId(101L);
        updated101.setNameAr("خيال علمي معدل");
        updated101.setNameEn("Sci-Fi Updated");

        SubGenreDTO newSub = new SubGenreDTO();
        newSub.setNameAr("مغامرة خيالية");
        newSub.setNameEn("Fantasy Adventure");

        MainGenreDTO requestDto = new MainGenreDTO();
        requestDto.setId(1L);
        requestDto.setNameAr("خيال");
        requestDto.setSubGenres(List.of(updated101, newSub));

        when(mainRepo.findById(1L)).thenReturn(Optional.of(existingGenre));
        when(mainRepo.save(any(MainGenre.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(genreMapper.toMainGenreDTO(any(MainGenre.class))).thenReturn(requestDto);

        commandService.save(requestDto);

        // Verify existing 101 was updated
        assertThat(existingGenre.getSubGenres()).hasSize(2);
        SubGenre s101 = existingGenre.getSubGenres().stream()
                .filter(s -> Long.valueOf(101L).equals(s.getId()))
                .findFirst().orElseThrow();
        assertThat(s101.getNameAr()).isEqualTo("خيال علمي معدل");
        assertThat(s101.getNameEn()).isEqualTo("Sci-Fi Updated");

        // Verify omitted 102 was removed
        boolean has102 = existingGenre.getSubGenres().stream()
                .anyMatch(s -> Long.valueOf(102L).equals(s.getId()));
        assertThat(has102).isFalse();

        // Verify new subgenre was added
        SubGenre sNew = existingGenre.getSubGenres().stream()
                .filter(s -> s.getId() == null)
                .findFirst().orElseThrow();
        assertThat(sNew.getNameAr()).isEqualTo("مغامرة خيالية");
        assertThat(sNew.getNameEn()).isEqualTo("Fantasy Adventure");
        assertThat(sNew.getMainGenre()).isEqualTo(existingGenre);
    }
}
