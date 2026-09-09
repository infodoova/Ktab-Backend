package com.doova.ktab.controller.v1.genre;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.genre.GenreCommandService;
import com.doova.ktab.service.genre.GenreQueryService;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/genres", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Genre Management API", description = "Endpoints for managing genres.")
public class GenreController {

    private final GenreCommandService genreCommandService;
    private final GenreQueryService genreQueryService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET ALL GENRES
    // ============================================================================================
    @Operation(summary = "Get all genres")
    @PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','READER')")
    @GetMapping("/getAllGenres")
    public ResponseEntity<ContentWrapper<MainGenreDTO>> getAllGenres() {
        return ResponseUtils.collection(genreQueryService.getAllGenres());
    }

    // ============================================================================================
    // GET GENRE BY ID
    // ============================================================================================
    @Operation(summary = "Get genre by ID")
    @PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','READER')")
    @GetMapping("/getGenreById/{id}")
    public ResponseEntity<ApiResponse<MainGenreDTO>> getGenreById(@PathVariable Long id) {

        MainGenreDTO genre = genreQueryService.getById(id);
        // If not found → GenreQueryService throws → GlobalExceptionHandler

        return ResponseUtils.success(genre, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), org.springframework.http.HttpStatus.OK);
    }

    // ============================================================================================
    // CREATE GENRE
    // ============================================================================================
    @Operation(summary = "Create genre")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PostMapping(path = "createGenre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> createGenre(@Valid @RequestPart("genreDto") MainGenreDTO genreDto, @RequestPart(value = "subGenres", required = false) List<@Valid SubGenreDTO> subGenres) {
        genreDto.setSubGenres(subGenres);

        MainGenreDTO created = genreCommandService.save(genreDto);

        return ResponseUtils.created(created, ApiMessageKey.GENRE_CREATED_SUCCESS.getMessage(messageSource));
    }

    // ============================================================================================
    // UPDATE GENRE
    // ============================================================================================
    @Operation(summary = "Update genre")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PatchMapping(path = "updateGenre/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> updateGenre(@PathVariable Long id, @Valid @RequestPart("genreDto") MainGenreDTO genreDto, @RequestPart(value = "subGenres", required = false) List<@Valid SubGenreDTO> subGenres) {
        genreDto.setId(id);
        genreDto.setSubGenres(subGenres);

        MainGenreDTO updated = genreCommandService.save(genreDto);

        return ResponseUtils.success(updated, ApiMessageKey.GENRE_UPDATED_SUCCESS.getMessage(messageSource), org.springframework.http.HttpStatus.OK);
    }
}
