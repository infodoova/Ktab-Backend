package com.doova.ktab.specification;

import com.doova.ktab.features.story.dto.StorySearchRequest;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.search.ArabicSearchUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class StorySpecification {

    private StorySpecification() {}

    public static Specification<Story> forSearch(StorySearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(req.q())) {
                String pattern = ArabicSearchUtils.toNormalizedLikePattern(req.q());
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate genreMatch = cb.like(cb.lower(root.get("genre")), pattern);

                Join<Story, User> authorJoin = root.join("author", JoinType.LEFT);
                Predicate authorFirstMatch = cb.like(cb.lower(authorJoin.get("firstName")), pattern);
                Predicate authorLastMatch = cb.like(cb.lower(authorJoin.get("lastName")), pattern);

                predicates.add(cb.or(titleMatch, genreMatch, authorFirstMatch, authorLastMatch));
            }

            if (StringUtils.hasText(req.genre())) {
                predicates.add(cb.equal(cb.lower(root.get("genre")), req.genre().toLowerCase().trim()));
            }

            if (req.lens() != null) {
                predicates.add(cb.equal(root.get("lens"), req.lens()));
            }

            if (req.authorId() != null) {
                predicates.add(cb.equal(root.get("author").get("id"), req.authorId()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
