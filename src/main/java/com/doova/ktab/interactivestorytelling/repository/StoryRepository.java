package com.doova.ktab.interactivestorytelling.repository;

import com.doova.ktab.interactivestorytelling.model.Story;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {
    Page<Story> findAllByAuthorId(Long authorId, Pageable pageable);
}
