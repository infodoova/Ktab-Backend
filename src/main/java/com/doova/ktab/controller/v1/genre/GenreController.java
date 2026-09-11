package com.doova.ktab.controller.v1.genre;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.genre.GenreCommandService;
import com.doova.ktab.service.genre.GenreQueryService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/genres", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Genre Management API", description = "Endpoints for managing and browsing literary genres.")
public class GenreController {

    private final GenreCommandService genreCommandService;
    private final GenreQueryService genreQueryService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET ALL GENRES
    // ============================================================================================
    @Operation(summary = "Get all genres")
    @PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','READER','LIBRARIAN','ADMIN_LIBRARIAN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MainGenreDTO>>> getAllGenres() {
        List<MainGenreDTO> genres = genreQueryService.getAllGenres();
        return ResponseUtils.success(genres, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET GENRE BY ID
    // ============================================================================================
    @Operation(summary = "Get genre by ID")
    @PreAuthorize("hasAnyAuthority('ADMIN','AUTHOR','READER','LIBRARIAN','ADMIN_LIBRARIAN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MainGenreDTO>> getGenreById(@PathVariable Long id) {
        MainGenreDTO genre = genreQueryService.getById(id);
        return ResponseUtils.success(genre, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // CREATE GENRE (JSON)
    // ============================================================================================
    @Operation(summary = "Create genre via JSON payload")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> createGenre(@Valid @RequestBody MainGenreDTO genreDto) {
        MainGenreDTO created = genreCommandService.save(genreDto);
        return ResponseUtils.created(created, ApiMessageKey.GENRE_CREATED_SUCCESS.getMessage(messageSource));
    }

    // ============================================================================================
    // CREATE GENRE (MULTIPART - BACKWARDS COMPATIBILITY)
    // ============================================================================================
    @Operation(summary = "Create genre via multipart/form-data (legacy)")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> createGenreMultipart(
            @Valid @RequestPart("genreDto") MainGenreDTO genreDto,
            @RequestPart(value = "subGenres", required = false) List<@Valid SubGenreDTO> subGenres
    ) {
        if (subGenres != null) {
            genreDto.setSubGenres(subGenres);
        }
        MainGenreDTO created = genreCommandService.save(genreDto);
        return ResponseUtils.created(created, ApiMessageKey.GENRE_CREATED_SUCCESS.getMessage(messageSource));
    }

    // ============================================================================================
    // UPDATE GENRE (JSON)
    // ============================================================================================
    @Operation(summary = "Update genre via JSON payload")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> updateGenre(
            @PathVariable Long id,
            @Valid @RequestBody MainGenreDTO genreDto
    ) {
        genreDto.setId(id);
        MainGenreDTO updated = genreCommandService.save(genreDto);
        return ResponseUtils.success(updated, ApiMessageKey.GENRE_UPDATED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // UPDATE GENRE (MULTIPART - BACKWARDS COMPATIBILITY)
    // ============================================================================================
    @Operation(summary = "Update genre via multipart/form-data (legacy)")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MainGenreDTO>> updateGenreMultipart(
            @PathVariable Long id,
            @Valid @RequestPart("genreDto") MainGenreDTO genreDto,
            @RequestPart(value = "subGenres", required = false) List<@Valid SubGenreDTO> subGenres
    ) {
        genreDto.setId(id);
        if (subGenres != null) {
            genreDto.setSubGenres(subGenres);
        }
        MainGenreDTO updated = genreCommandService.save(genreDto);
        return ResponseUtils.success(updated, ApiMessageKey.GENRE_UPDATED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
