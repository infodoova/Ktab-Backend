package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookAboutAudioResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.service.book.BookAboutAudioService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/books/{bookId}/about-audio", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
@Tag(name = "Admin Book Audio API", description = "Upload the short audio that introduces a book.")
public class AdminBookAboutAudioController {

    private final BookAboutAudioService aboutAudioService;
    private final MessageSource messageSource;

    @Operation(summary = "Upload (or replace) the audio that introduces a book",
            description = "The audio is recorded elsewhere. It is stored in cloud storage and its path and description are saved, "
                    + "and from then on it is returned with the book. MP3, M4A, WAV, AAC or OGG, at most 15 MB.")
    @PutMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BookAboutAudioResponse>> upload(
            @PathVariable Long bookId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException(ApiMessageKey.FILE_UPLOAD_FAILED, e);
        }
        BookAboutAudioResponse saved = aboutAudioService.save(bookId, content, file.getOriginalFilename(), description, durationSeconds);
        return ResponseUtils.success(saved, ApiMessageKey.BOOK_ABOUT_AUDIO_SAVED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get the audio that introduces a book")
    @GetMapping
    public ResponseEntity<ApiResponse<BookAboutAudioResponse>> get(@PathVariable Long bookId) {
        BookAboutAudioResponse audio = aboutAudioService.find(bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.BOOK_ABOUT_AUDIO_NOT_FOUND));
        return ResponseUtils.success(audio, ApiMessageKey.BOOK_ABOUT_AUDIO_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Remove the audio that introduces a book")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long bookId) {
        aboutAudioService.delete(bookId);
        return ResponseUtils.success(null, ApiMessageKey.BOOK_ABOUT_AUDIO_DELETED.getMessage(messageSource), HttpStatus.OK);
    }
}
