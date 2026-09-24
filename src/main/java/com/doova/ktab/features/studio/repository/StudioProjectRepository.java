package com.doova.ktab.features.studio.repository;

import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudioProjectRepository extends JpaRepository<StudioProject, Long> {

    Optional<StudioProject> findByExternalProjectId(String externalProjectId);

    @Query("""
                select p from StudioProject p
                where p.book.id = :bookId and p.lifecycle not in ('DELETED', 'FAILED', 'ARCHIVED')
            """)
    Optional<StudioProject> findLiveByBookId(@Param("bookId") Long bookId);

    /**
     * Closes out any live project for a book without confirming remote deletion — used by
     * {@code BookContentPurger} so a reroute/re-ingestion is never blocked by the partial
     * unique index on (bookId, live lifecycle). The orphan reconciler picks these up later.
     */
    @Modifying
    @Query("""
                update StudioProject p set p.lifecycle = 'ARCHIVED'
                where p.book.id = :bookId and p.lifecycle not in ('DELETED', 'FAILED', 'ARCHIVED')
            """)
    void archiveLiveByBookId(@Param("bookId") Long bookId);

    @Query("""
                select p from StudioProject p
                where p.lifecycle in ('ARCHIVED', 'FAILED')
                  and p.projectDeletedAt is null
                  and p.updatedAt < :cutoff
            """)
    List<StudioProject> findStaleUndeleted(@Param("cutoff") java.time.LocalDateTime cutoff);

    List<StudioProject> findByLifecycle(StudioProjectLifecycle lifecycle);
}
