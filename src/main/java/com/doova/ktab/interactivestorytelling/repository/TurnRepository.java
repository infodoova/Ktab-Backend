package com.doova.ktab.interactivestorytelling.repository;

import com.doova.ktab.interactivestorytelling.model.ReadingSession;
import com.doova.ktab.interactivestorytelling.model.Turn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnRepository extends JpaRepository<Turn, Long> {

    @Query("select t from Turn t where t.session = :session order by t.turnIndex desc limit 1")
    Turn findLatest(ReadingSession session);

    List<Turn> findTop2BySessionIdOrderByTurnIndexDesc(Long sessionId);

    List<Turn> findBySessionIdOrderByTurnIndexAsc(Long sessionId);

    List<Turn> findBySessionIdAndTurnIndexBetweenOrderByTurnIndexAsc(Long sessionId, int start, int end);
}
