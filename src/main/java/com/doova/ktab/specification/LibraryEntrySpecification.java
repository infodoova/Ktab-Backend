package com.doova.ktab.specification;

import com.doova.ktab.dto.library.PersonalLibrarySearchRequest;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookLibraryEntry;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class LibraryEntrySpecification {

    private LibraryEntrySpecification() {}

    public static Specification<BookLibraryEntry> forUser(User user, PersonalLibrarySearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Strictly isolate to the requesting user
            predicates.add(cb.equal(root.get("user").get("id"), user.getId()));

            Join<BookLibraryEntry, Book> bookJoin = root.join("book", JoinType.INNER);

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                Predicate titleMatch = cb.like(cb.lower(bookJoin.get("title")), pattern);
                Predicate authorMatch = cb.like(cb.lower(bookJoin.get("customAuthorName")), pattern);

                Join<Book, User> authorJoin = bookJoin.join("author", JoinType.LEFT);
                Predicate authorFirstMatch = cb.like(cb.lower(authorJoin.get("firstName")), pattern);
                Predicate authorLastMatch = cb.like(cb.lower(authorJoin.get("lastName")), pattern);

                predicates.add(cb.or(titleMatch, authorMatch, authorFirstMatch, authorLastMatch));
            }

            if (req.isFavorite() != null) {
                predicates.add(cb.equal(root.get("isFavorite"), req.isFavorite()));
            }

            if (req.mainGenreId() != null) {
                predicates.add(cb.equal(bookJoin.get("mainGenre").get("id"), req.mainGenreId()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
