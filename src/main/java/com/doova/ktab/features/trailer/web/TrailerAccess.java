package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** R1: author -> own books, librarian/admin-librarian -> their organization's books, admin -> any. */
@Component
@RequiredArgsConstructor
public class TrailerAccess {

    private final BookRepository books;
    private final UserRepository users;

    public Book requireBook(User user, Long bookId) {
        String role = user.getRole();
        Optional<Book> book;
        if (UserRole.ADMIN.getCode().equals(role)) {
            book = books.findById(bookId);
        } else if (UserRole.AUTHOR.getCode().equals(role)) {
            book = books.findByIdAndAuthor(bookId, user);
        } else if (UserRole.LIBRARIAN.getCode().equals(role) || UserRole.ADMIN_LIBRARIAN.getCode().equals(role)) {
            User managed = users.findById(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));
            if (managed.getLibraryOrganization() == null) {
                throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
            }
            book = books.findByIdAndLibraryOrganizationId(bookId, managed.getLibraryOrganization().getId());
        } else {
            book = Optional.empty();
        }
        return book.orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
    }

    public boolean isAdmin(User user) {
        return UserRole.ADMIN.getCode().equals(user.getRole());
    }
}
