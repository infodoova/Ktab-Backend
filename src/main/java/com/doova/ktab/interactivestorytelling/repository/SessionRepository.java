package com.doova.ktab.interactivestorytelling.repository;

import com.doova.ktab.interactivestorytelling.model.ReadingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<ReadingSession, Long> {
    Optional<ReadingSession> findByStoryIdAndReaderId(Long storyId, Long readerId);
    
    boolean existsByStoryId(Long storyId);
}
