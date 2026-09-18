package com.doova.ktab.service.library;

import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.library.LibrarySort;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.pagination.PageResponse;

import java.util.List;

public interface LibraryService {

    void assignBookToUser(AssignBookRequest request, User reader);

    List<BookResponseDto> getUserLibrary(Long userId);

    PageResponse<BookResponseDto> getUserLibrary(Long userId, int page, int size, LibrarySort sort);

    void removeBookFromLibrary(Long userId, Long bookId);

    boolean isAssigned(Long userId, Long bookId);

    PageResponse<BookResponseDto> searchUserLibrary(
            User reader,
            com.doova.ktab.dto.library.PersonalLibrarySearchRequest req,
            org.springframework.data.domain.Pageable pageable
    );
}
