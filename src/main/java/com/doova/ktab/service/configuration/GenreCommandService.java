package com.doova.ktab.service.configuration;

import com.doova.ktab.api.dto.MainGenreDTO;
import com.doova.ktab.api.dto.SubGenreDTO;
import com.doova.ktab.mappers.configuration.GenreMapper;
import com.doova.ktab.model.configuration.MainGenre;
import com.doova.ktab.model.configuration.SubGenre;
import com.doova.ktab.repository.configuration.MainGenreRepository;
import com.doova.ktab.repository.configuration.SubGenreRepository;
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
    public MainGenreDTO save(MainGenreDTO mainGenreDTO) {

        // Check if the genre exists (update) or is new (create)
        MainGenre mainGenre = mainGenreDTO.getId() != null ? mainRepo.findById(mainGenreDTO.getId()).orElseThrow(() -> new EntityNotFoundException("Main genre not found")) : new MainGenre();

        // Update main genre fields
        mainGenre.setNameEn(mainGenreDTO.getNameEn());
        mainGenre.setNameAr(mainGenreDTO.getNameAr());
        mainGenre.setDescription(mainGenreDTO.getDescription());

        // Handle sub-genres
        syncSubGenres(mainGenre, mainGenreDTO.getSubGenres());

        // Save the genre (insert or update)
        MainGenre savedMainGenre = mainRepo.save(mainGenre);

        // Convert back to DTO and return
        return genreMapper.toMainGenreDTO(savedMainGenre);
    }

    /**
     * Sync sub-genres:
     * - Add new sub-genres
     * - Update existing ones
     * - Remove sub-genres if necessary
     */
    private void syncSubGenres(MainGenre mainGenre, List<SubGenreDTO> subGenreDTOs) {

        // If sub-genres are null or empty, clear all current sub-genres
        if (subGenreDTOs == null || subGenreDTOs.isEmpty()) {
            mainGenre.clearSubGenres();
            return;
        }

        // Map existing sub-genres by ID
        Map<Long, SubGenre> existingSubGenres = mainGenre.getSubGenres().stream().collect(Collectors.toMap(SubGenre::getId, Function.identity()));

        // For each sub-genre DTO, add it or update it in the entity
        for (SubGenreDTO subGenreDTO : subGenreDTOs) {
            SubGenre subGenre = subGenreDTO.getId() != null && existingSubGenres.containsKey(subGenreDTO.getId()) ? existingSubGenres.get(subGenreDTO.getId()) : new SubGenre();

            subGenre.setNameEn(subGenreDTO.getNameEn());
            subGenre.setNameAr(subGenreDTO.getNameAr());
            subGenre.setDescription(subGenreDTO.getDescription());

            mainGenre.addSubGenre(subGenre); // add new or update existing sub-genre
        }
    }
}
