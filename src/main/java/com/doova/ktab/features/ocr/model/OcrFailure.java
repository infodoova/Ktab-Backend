package com.doova.ktab.features.ocr.model;

import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.book.Book;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_ocr_failures", indexes = {@Index(name = "idx_ocr_failures_book", columnList = "book_id"), @Index(name = "idx_ocr_failures_page", columnList = "page_number")}, uniqueConstraints = {@UniqueConstraint(name = "uk_ocr_failure_book_page", columnNames = {"book_id", "page_number"})})
@Getter
@Setter
public class OcrFailure extends BaseEntity {

    /**
     * Parent book reference (lazy to keep batch fast)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ocr_failures_book"))
    private Book book;

    /**
     * Failed page number
     */
    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    /**
     * S3 object key of the page image
     */
    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    /**
     * Error message from OCR / Gemini / Vertex
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of retry attempts
     */
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;
}
