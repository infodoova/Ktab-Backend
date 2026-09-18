package com.doova.ktab.specification;

import com.doova.ktab.dto.library.LibraryOrganizationSearchRequest;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class LibraryOrganizationSpecification {

    private LibraryOrganizationSpecification() {}

    public static Specification<LibraryOrganization> forActiveDirectory(LibraryOrganizationSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                Predicate cityMatch = cb.like(cb.lower(root.get("city")), pattern);
                predicates.add(cb.or(nameMatch, descMatch, cityMatch));
            }

            if (StringUtils.hasText(req.city())) {
                predicates.add(cb.like(cb.lower(root.get("city")),
                        ArabicSearchUtils.toNormalizedLikePattern(req.city())));
            }

            if (StringUtils.hasText(req.country())) {
                predicates.add(cb.like(cb.lower(root.get("country")),
                        ArabicSearchUtils.toNormalizedLikePattern(req.country())));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
