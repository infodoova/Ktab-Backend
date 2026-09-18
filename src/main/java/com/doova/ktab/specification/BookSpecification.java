package com.doova.ktab.specification;

import com.doova.ktab.dto.book.AdvancedBookSearchRequest;
import com.doova.ktab.dto.book.AuthorBookSearchRequest;
import com.doova.ktab.dto.book.LibrarianBookSearchRequest;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Composable, type-safe JPA Specifications for querying Books with Arabic NLP normalization.
 */
public final class BookSpecification {

    private BookSpecification() {}

    /**
     * Builds a specification for public reader discovery (enforces PUBLISHED status).
     */
    public static Specification<Book> forDiscovery(AdvancedBookSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always enforce PUBLISHED status for public discovery
            predicates.add(cb.equal(root.get("status"), BookStatus.PUBLISHED));

            applyCommonFilters(req, root, cb, predicates);

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Builds a specification strictly scoped to the authenticated author.
     */
    public static Specification<Book> forAuthor(User author, AuthorBookSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Strictly isolate to the current author
            predicates.add(cb.equal(root.get("author").get("id"), author.getId()));

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            if (req.status() != null) {
                predicates.add(cb.equal(root.get("status"), req.status()));
            }

            if (req.ocrStatus() != null) {
                predicates.add(cb.equal(root.get("ocrStatus"), req.ocrStatus()));
            }

            if (req.hasAudio() != null) {
                predicates.add(cb.equal(root.get("hasAudio"), req.hasAudio()));
            }

            if (req.mainGenreId() != null) {
                predicates.add(cb.equal(root.get("mainGenre").get("id"), req.mainGenreId()));
            }

            if (req.subGenreId() != null) {
                predicates.add(cb.equal(root.get("subGenre").get("id"), req.subGenreId()));
            }

            if (req.createdAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), req.createdAfter()));
            }

            if (req.createdBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), req.createdBefore()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Builds a specification strictly scoped to the librarian's organization.
     */
    public static Specification<Book> forLibrarian(Long organizationId, LibrarianBookSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Strictly isolate to the librarian's organization
            predicates.add(cb.equal(root.get("libraryOrganization").get("id"), organizationId));

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate authorMatch = cb.like(cb.lower(root.get("customAuthorName")), pattern);
                predicates.add(cb.or(titleMatch, authorMatch));
            }

            if (StringUtils.hasText(req.customAuthorName())) {
                predicates.add(cb.like(cb.lower(root.get("customAuthorName")),
                        ArabicSearchUtils.toNormalizedLikePattern(req.customAuthorName())));
            }

            if (StringUtils.hasText(req.language())) {
                predicates.add(cb.equal(root.get("language"), req.language()));
            }

            if (req.status() != null) {
                predicates.add(cb.equal(root.get("status"), req.status()));
            }

            if (req.ocrStatus() != null) {
                predicates.add(cb.equal(root.get("ocrStatus"), req.ocrStatus()));
            }

            if (req.hasAudio() != null) {
                predicates.add(cb.equal(root.get("hasAudio"), req.hasAudio()));
            }

            if (req.mainGenreId() != null) {
                predicates.add(cb.equal(root.get("mainGenre").get("id"), req.mainGenreId()));
            }

            if (req.subGenreId() != null) {
                predicates.add(cb.equal(root.get("subGenre").get("id"), req.subGenreId()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void applyCommonFilters(
            AdvancedBookSearchRequest req,
            jakarta.persistence.criteria.Root<Book> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<Predicate> predicates
    ) {
        // Free-text fuzzy search across title, description, custom author name, and registered author name
        if (StringUtils.hasText(req.q())) {
            String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
            Predicate customAuthorMatch = cb.like(cb.lower(root.get("customAuthorName")), pattern);

            Join<Book, User> authorJoin = root.join("author", JoinType.LEFT);
            Predicate authorFirstMatch = cb.like(cb.lower(authorJoin.get("firstName")), pattern);
            Predicate authorLastMatch = cb.like(cb.lower(authorJoin.get("lastName")), pattern);

            predicates.add(cb.or(titleMatch, descMatch, customAuthorMatch, authorFirstMatch, authorLastMatch));
        }

        // Specific title filter
        if (StringUtils.hasText(req.title())) {
            predicates.add(cb.like(cb.lower(root.get("title")), ArabicSearchUtils.toNormalizedLikePattern(req.title())));
        }

        // Specific author name filter
        if (StringUtils.hasText(req.authorName())) {
            String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.authorName());
            Predicate customAuthorMatch = cb.like(cb.lower(root.get("customAuthorName")), pattern);
            Join<Book, User> authorJoin = root.join("author", JoinType.LEFT);
            Predicate authorFirstMatch = cb.like(cb.lower(authorJoin.get("firstName")), pattern);
            Predicate authorLastMatch = cb.like(cb.lower(authorJoin.get("lastName")), pattern);
            predicates.add(cb.or(customAuthorMatch, authorFirstMatch, authorLastMatch));
        }

        if (req.authorId() != null) {
            predicates.add(cb.equal(root.get("author").get("id"), req.authorId()));
        }

        if (req.libraryOrganizationId() != null) {
            predicates.add(cb.equal(root.get("libraryOrganization").get("id"), req.libraryOrganizationId()));
        }

        if (req.mainGenreIds() != null && !req.mainGenreIds().isEmpty()) {
            predicates.add(root.get("mainGenre").get("id").in(req.mainGenreIds()));
        }

        if (req.subGenreIds() != null && !req.subGenreIds().isEmpty()) {
            predicates.add(root.get("subGenre").get("id").in(req.subGenreIds()));
        }

        if (req.minAge() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("ageRangeMax"), req.minAge()));
        }

        if (req.maxAge() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("ageRangeMin"), req.maxAge()));
        }

        if (req.minRating() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("averageRating"), req.minRating()));
        }

        if (req.maxRating() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("averageRating"), req.maxRating()));
        }

        if (req.hasAudio() != null) {
            predicates.add(cb.equal(root.get("hasAudio"), req.hasAudio()));
        }

        if (req.bookSource() != null) {
            predicates.add(cb.equal(root.get("bookSource"), req.bookSource()));
        }

        if (req.fromYear() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("publishDate"),
                    LocalDate.of(req.fromYear(), 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
            ));
        }

        if (req.toYear() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    root.get("publishDate"),
                    LocalDate.of(req.toYear(), 12, 31).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
            ));
        }
    }
}
