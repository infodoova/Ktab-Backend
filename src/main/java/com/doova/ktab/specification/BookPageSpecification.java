package com.doova.ktab.specification;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class BookPageSpecification {

    private BookPageSpecification() {}

    public static Specification<BookPage> forBookContent(Long bookId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("book").get("id"), bookId));
            predicates.add(cb.equal(root.get("status"), OcrStatus.COMPLETED));

            if (StringUtils.hasText(keyword)) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(keyword);
                predicates.add(cb.like(cb.lower(root.get("markdownContent")), pattern));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
