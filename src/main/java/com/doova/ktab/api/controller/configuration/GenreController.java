package com.doova.ktab.api.controller.configuration;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.api.dto.MainGenreDTO;
import com.doova.ktab.api.dto.SubGenreDTO;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.service.configuration.GenreCommandService;
import com.doova.ktab.service.configuration.GenreQueryService;
import com.doova.ktab.utils.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/genres", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Genre Management API", description = "Endpoints for managing genres, including book-related operations.")
public class GenreController {

    private final GenreCommandService genreCommandService;
    private final GenreQueryService genreQueryService;

    // ============================================================================================
    // GET GENRES (PAGINATED)
    // ============================================================================================
    @Operation(summary = "Get all genres", description = "Retrieves paginated list of all genres.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Genres retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AUTHOR', 'READER')")
    @GetMapping("/getAllGenres")
    public ResponseEntity<ContentWrapper<MainGenreDTO>> getAllGenres() {
        List<MainGenreDTO> genres = genreQueryService.getAllGenres();
        return ResponseUtils.response(genres);
    }

    // ============================================================================================
    // GET GENRE BY ID
    // ============================================================================================
    @Operation(summary = "Get genre by ID", description = "Retrieves a specific genre by its ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Genre retrieved", content = @Content(schema = @Schema(implementation = MainGenreDTO.class)))
    @PreAuthorize("hasAnyAuthority('ADMIN', 'AUTHOR','READER')")
    @GetMapping("/getGenreById/{id}")
    public ResponseEntity<ApiResponse<MainGenreDTO>> getGenreById(@PathVariable Long id) {
        try {
            MainGenreDTO genre = genreQueryService.getById(id);
            return ResponseUtils.response(genre);
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound("Genre not found");
        }
    }

    // ============================================================================================
    // CREATE GENRE (MULTIPART)
    // ============================================================================================
    @Operation(summary = "Create a new genre", description = "Creates a new genre with optional sub-genres.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Genre created", content = @Content(schema = @Schema(implementation = MainGenreDTO.class)))
    @PostMapping(path = "createGenre", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<MainGenreDTO>> createGenre(@Validated @RequestPart("genreDto") MainGenreDTO genreDto, @RequestPart(value = "subGenres", required = false) List<SubGenreDTO> subGenres) {
        try {
            genreDto.setSubGenres(subGenres);
            MainGenreDTO createdGenre = genreCommandService.save(genreDto);
            return ResponseUtils.created(createdGenre);
        } catch (Exception ex) {
            throw ResponseUtils.errorResponse("Failed to create genre: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ============================================================================================
    // UPDATE GENRE (MULTIPART)
    // ============================================================================================
    @Operation(summary = "Update genre", description = "Updates genre and optionally its sub-genres.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Genre updated", content = @Content(schema = @Schema(implementation = MainGenreDTO.class)))
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    @PatchMapping(path = "updateGenre/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<MainGenreDTO>> updateGenre(@PathVariable Long id, @Validated @RequestPart("genreDto") MainGenreDTO genreDto, @RequestPart(value = "subGenres", required = false) List<SubGenreDTO> subGenres) {
        try {
            genreDto.setSubGenres(subGenres);
            genreDto.setId(id);
            MainGenreDTO updatedGenre = genreCommandService.save(genreDto);
            return ResponseUtils.response(updatedGenre, "Genre updated successfully");
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound("Genre not found");
        } catch (Exception ex) {
            throw ResponseUtils.errorResponse("Failed to update genre: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ============================================================================================
    // DELETE GENRE
    // ============================================================================================
//    @Operation(summary = "Delete genre", description = "Deletes a genre and associated sub-genres.")
//    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Genre deleted")
//    @PreAuthorize("hasAnyAuthority('ADMIN', 'AUTHOR')")
//    @DeleteMapping("deleteGenre/{id}")
//    public ResponseEntity<ApiResponse<Object>> deleteGenre(@PathVariable Long id) {
//        try {
//            genreCommandService.deleteGenre(id);
//            return ResponseUtils.response(null, "Genre deleted successfully");
//        } catch (EntityNotFoundException ex) {
//            throw ResponseUtils.notFound("Genre not found");
//        }
//    }
}
