package com.doova.ktab.controller.v1.library;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.request.AssignBookRequest;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.enums.LibrarySort;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.library.LibraryService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/library", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "User Library API", description = "Manage user’s personal book library.")
public class LibraryController {

    private final LibraryService libraryService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET USER LIBRARY
    // ============================================================================================
    @Operation(summary = "Get user's library")
    @GetMapping("/myLibrary")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<?> getUserLibrary(@CurrentUser User reader, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, @RequestParam(defaultValue = "RECENT") LibrarySort sort) {

        String message = ApiMessageKey.LIBRARY_FETCH_SUCCESS.getMessage(messageSource);

        Object data;

        // FULL LIST (no pagination)
        if (page == null || size == null) {
            data = libraryService.getUserLibrary(reader.getId());
        }
        // PAGINATED
        else {
            data = libraryService.getUserLibrary(reader.getId(), page, size, sort);
        }

        return ResponseUtils.success(data, message, HttpStatus.OK);
    }


    // ============================================================================================
    // REMOVE BOOK
    // ============================================================================================
    @Operation(summary = "Remove a book from user's library")
    @DeleteMapping("/removeBook/{bookId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<Void>> removeBookFromLibrary(@PathVariable Long bookId, @CurrentUser User reader) {
        libraryService.removeBookFromLibrary(reader.getId(), bookId);

        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_BOOK_REMOVED.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // ASSIGN BOOK
    // ============================================================================================
    @Operation(summary = "Assign a book to user's library")
    @PostMapping("/assignBook")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<Void>> assignBookToUser(@RequestBody AssignBookRequest request, @CurrentUser User reader) {
        libraryService.assignBookToUser(request, reader);

        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_BOOK_ADDED.getMessage(messageSource), HttpStatus.CREATED);
    }

    // ============================================================================================
    // CHECK ASSIGNMENT
    // ============================================================================================
    @Operation(summary = "Check if a book is assigned to the user's library")
    @GetMapping("/isAssigned/{bookId}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<Boolean>> isBookAssigned(@PathVariable Long bookId, @CurrentUser User reader) {
        boolean assigned = libraryService.isAssigned(reader.getId(), bookId);

        return ResponseUtils.success(assigned, ApiMessageKey.LIBRARY_CHECK_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
