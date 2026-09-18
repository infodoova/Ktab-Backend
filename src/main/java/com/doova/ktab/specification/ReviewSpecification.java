package com.doova.ktab.specification;

import com.doova.ktab.dto.review.ReviewSearchRequest;
import com.doova.ktab.model.book.BookReview;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class ReviewSpecification {

    private ReviewSpecification() {}

    public static Specification<BookReview> forBookReviews(Long bookId, ReviewSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("book").get("id"), bookId));

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                predicates.add(cb.like(cb.lower(root.get("comment")), pattern));
            }

            if (req.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), req.minRating()));
            }

            if (req.maxRating() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("rating"), req.maxRating()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
