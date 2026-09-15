package com.doova.ktab.features.story.repository;

import com.doova.ktab.features.story.model.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    @EntityGraph(attributePaths = {"author"})
    @Query("SELECT s FROM Story s")
    Page<Story> findAllWithAuthor(Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    @Query("SELECT s FROM Story s WHERE s.id = :id")
    Optional<Story> findWithAuthorById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"author"})
    Page<Story> findAllByAuthorId(Long authorId, Pageable pageable);
}
