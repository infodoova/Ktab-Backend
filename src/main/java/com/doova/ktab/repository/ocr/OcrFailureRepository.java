package com.doova.ktab.repository.ocr;

import com.doova.ktab.model.ocr.OcrFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OcrFailureRepository extends JpaRepository<OcrFailure, Long> {
    Optional<OcrFailure> findByBook_IdAndPageNumber(Long bookId, Integer pageNumber);
}
