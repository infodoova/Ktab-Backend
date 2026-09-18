package com.doova.ktab.controller.v1.library;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.enums.library.LibrarySort;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.library.LibraryService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/library", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('READER')")
@Tag(name = "User Library API", description = "Manage user’s personal book library.")
public class LibraryController {

    private final LibraryService libraryService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET USER LIBRARY (PAGINATED)
    // ============================================================================================
    @Operation(summary = "Get user's library (paginated)")
    @GetMapping({ "", "/my-library", "/myLibrary" })
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getUserLibrary(
            @CurrentUser User reader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "RECENT") LibrarySort sort) {
        PageResponse<BookResponseDto> data = libraryService.getUserLibrary(reader.getId(), page, size, sort);
        return ResponseUtils.success(data, ApiMessageKey.LIBRARY_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    // ============================================================================================
    // SEARCH USER LIBRARY
    // ============================================================================================
    @Operation(summary = "Search books inside user's personal library")
    @PostMapping({ "/my-library/search", "/search" })
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> searchUserLibrary(
            @CurrentUser User reader,
            @Valid @RequestBody com.doova.ktab.dto.library.PersonalLibrarySearchRequest requestDto) {
        String sortProperty = requestDto.sortBy() != null ? requestDto.sortBy() : "addedAt";
        org.springframework.data.domain.Sort.Direction direction = requestDto.sortDirection() != null
                ? requestDto.sortDirection()
                : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                requestDto.page(), requestDto.size(), org.springframework.data.domain.Sort.by(direction, sortProperty));

        PageResponse<BookResponseDto> data = libraryService.searchUserLibrary(reader, requestDto, pageable);
        return ResponseUtils.success(data, ApiMessageKey.LIBRARY_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    // ============================================================================================
    // GET USER LIBRARY (UNPAGED)
    // ============================================================================================
    @Operation(summary = "Get user's full library (unpaged)")
    @GetMapping({ "/all", "/all-books" })
    public ResponseEntity<ApiResponse<List<BookResponseDto>>> getUserLibraryFull(
            @CurrentUser User reader) {
        List<BookResponseDto> data = libraryService.getUserLibrary(reader.getId());
        return ResponseUtils.success(data, ApiMessageKey.LIBRARY_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    // ============================================================================================
    // REMOVE BOOK
    // ============================================================================================
    @Operation(summary = "Remove a book from user's library")
    @DeleteMapping({ "/books/{bookId}", "/removeBook/{bookId}" })
    public ResponseEntity<ApiResponse<Void>> removeBookFromLibrary(
            @PathVariable Long bookId,
            @CurrentUser User reader) {
        libraryService.removeBookFromLibrary(reader.getId(), bookId);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_BOOK_REMOVED.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // ASSIGN BOOK
    // ============================================================================================
    @Operation(summary = "Assign a book to user's library")
    @PostMapping({ "/books", "/assignBook" })
    public ResponseEntity<ApiResponse<Void>> assignBookToUser(
            @Valid @RequestBody AssignBookRequest request,
            @CurrentUser User reader) {
        libraryService.assignBookToUser(request, reader);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_BOOK_ADDED.getMessage(messageSource),
                HttpStatus.CREATED);
    }

    // ============================================================================================
    // CHECK ASSIGNMENT
    // ============================================================================================
    @Operation(summary = "Check if a book is assigned to the user's library")
    @GetMapping({ "/books/{bookId}/status", "/isAssigned/{bookId}" })
    public ResponseEntity<ApiResponse<Boolean>> isBookAssigned(
            @PathVariable Long bookId,
            @CurrentUser User reader) {
        boolean assigned = libraryService.isAssigned(reader.getId(), bookId);
        return ResponseUtils.success(assigned, ApiMessageKey.LIBRARY_CHECK_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }
}
