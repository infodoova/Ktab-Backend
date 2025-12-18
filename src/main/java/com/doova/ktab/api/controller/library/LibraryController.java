package com.doova.ktab.api.controller.library;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.annotation.UserMatchesOrAdmin;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.api.dto.request.AssignBookRequest;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.LibrarySort;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.library.LibraryService;
import com.doova.ktab.utils.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.utils.wrapper.ContentWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/library", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "User Library API", description = "Manage user’s personal book library.")
public class LibraryController {

    private final LibraryService libraryService;

    @Operation(summary = "Get user's library (full or paginated + sorted)", description = """
            If page & size are provided → returns paginated library.
            If not provided → returns full library list.
            Supports sorting: RECENT, TITLE, RATING.
            """)
    @GetMapping("/myLibrary")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<?> getUserLibrary(@CurrentUser User reader, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "RECENT") LibrarySort sort) {

        // CASE 1 — Return full list (no pagination params)
        if (page == null || size == null) {
            List<BookResponseDto> books = libraryService.getUserLibrary(reader.getId());
            return ResponseUtils.response(books);  // ContentWrapper<List<BookResponseDto>>
        }

        // CASE 2 — Paginated response
        PageResponse<BookResponseDto> books = libraryService.getUserLibrary(reader.getId(), page, size, sort);

        return ResponseUtils.response(books);  // ApiResponse<PageResponse<BookResponseDto>>
    }


    // ============================================================================================
    // REMOVE BOOK FROM LIBRARY
    // ============================================================================================
    @Operation(summary = "Remove a book from user's library", description = "Deletes the user-book relation, removing the book from the user's library.")
    @DeleteMapping("removeBook/{bookId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> removeBookFromLibrary(@PathVariable Long bookId, @CurrentUser User reader) {
        libraryService.removeBookFromLibrary(reader.getId(), bookId);
        return ResponseUtils.response("Book removed from library.");
    }

    // ============================================================================================
    // ASSIGN BOOK TO USER'S LIBRARY
    // ============================================================================================
    @Operation(summary = "Assign a book to the user's library", description = "Adds a book to the user's personal library.")
    @PostMapping("/assignBook")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> assignBookToUser(@RequestBody AssignBookRequest request, @CurrentUser User reader) {
        libraryService.assignBookToUser(request, reader);
        return ResponseUtils.response("Book successfully added to your library.");
    }

    @Operation(summary = "Check if a book is assigned to the user's library", description = "Returns true/false depending on whether the user already has the book in their library.")
    @GetMapping("/isAssigned/{bookId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<Boolean>> isBookAssigned(@PathVariable Long bookId, @CurrentUser User reader) {
        boolean assigned = libraryService.isAssigned(reader.getId(), bookId);
        return ResponseUtils.response(assigned);
    }


}
