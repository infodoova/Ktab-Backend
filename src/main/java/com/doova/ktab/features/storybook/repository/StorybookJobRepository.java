package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.StorybookJob;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StorybookJobRepository extends JpaRepository<StorybookJob, Long> {
    List<StorybookJob> findByStorybookIdOrderByIdAsc(Long storybookId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO tbl_storybook_jobs
                (col_storybook_id, col_step, col_page_index, col_generation, col_idempotency_key,
                 col_status, col_attempts, col_next_run_at, created_at, updated_at, version)
            VALUES (:bookId, :step, :pageIndex, :generation, :key, 'PENDING', 0, now(), now(), now(), 0)
            ON CONFLICT (col_idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("bookId") Long bookId,
                       @Param("step") String step,
                       @Param("pageIndex") int pageIndex,
                       @Param("generation") int generation,
                       @Param("key") String key);

    @Query(nativeQuery = true, value = """
            SELECT col_id FROM tbl_storybook_jobs
            WHERE col_status = 'PENDING' AND col_next_run_at <= now()
            ORDER BY col_next_run_at, col_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<Long> lockDueJobIds(@Param("limit") int limit);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE tbl_storybook_jobs
            SET col_status = 'RUNNING', col_locked_by = :worker, col_locked_at = now(),
                col_attempts = col_attempts + 1, updated_at = now(), version = version + 1
            WHERE col_id IN (:ids)
            """)
    int markRunning(@Param("ids") List<Long> ids, @Param("worker") String worker);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE tbl_storybook_jobs
            SET col_status = 'PENDING', col_locked_by = NULL, col_locked_at = NULL,
                updated_at = now(), version = version + 1
            WHERE col_status = 'RUNNING' AND col_locked_at < :lockedBefore
            """)
    int releaseStaleRunning(@Param("lockedBefore") Instant lockedBefore);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE tbl_storybook_jobs
            SET col_status = 'PENDING', col_attempts = 0, col_next_run_at = now(), col_finished_at = NULL,
                updated_at = now(), version = version + 1
            WHERE col_storybook_id = :bookId AND col_status = 'DEAD'
            """)
    int reviveDeadJobs(@Param("bookId") Long bookId);
}

