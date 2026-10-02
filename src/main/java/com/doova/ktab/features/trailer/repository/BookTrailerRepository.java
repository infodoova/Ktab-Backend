package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookTrailerRepository extends JpaRepository<BookTrailer, Long> {

    List<BookTrailer> findByBookIdOrderByIdDesc(Long bookId);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<TrailerStatus> statuses);

    long countByBookIdAndCreatedAtAfterAndStatusNotIn(Long bookId, LocalDateTime after, Collection<TrailerStatus> excluded);

    Optional<BookTrailer> findBySessionId(String sessionId);

    List<BookTrailer> findTop10ByStatusOrderByIdAsc(TrailerStatus status);

    List<BookTrailer> findByStatusOrderByIdAsc(TrailerStatus status, Pageable page);

    long countByStatus(TrailerStatus status);

    @Query("select t from BookTrailer t where t.status = com.doova.ktab.features.trailer.enums.TrailerStatus.RUNNING "
            + "and t.nextCheckAt <= :now order by t.nextCheckAt")
    List<BookTrailer> findDueRunning(@Param("now") Instant now, Pageable page);
}
