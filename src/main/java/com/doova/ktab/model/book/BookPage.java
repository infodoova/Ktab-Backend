package com.doova.ktab.model.book;

import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tbl_book_pages", uniqueConstraints = {@UniqueConstraint(name = "uq_book_pages_book_page", columnNames = {"col_book_id", "col_page_number"})}, indexes = {@Index(name = "idx_book_pages_book", columnList = "col_book_id"), @Index(name = "idx_book_pages_page", columnList = "col_page_number")})
@Getter
@Setter
public class BookPage extends BaseEntity {

    /**
     * Parent book
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_book_pages_book"))
    private Book book;

    /**
     * Page number inside the book
     */
    @Column(name = "col_page_number", nullable = false)
    private int pageNumber;

    /**
     * OCR result in Markdown format
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "col_markdown_content", nullable = false, columnDefinition = "TEXT")
    private String markdownContent;

    /**
     * OCR status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "col_ocr_status", nullable = false)
    private OcrStatus status = OcrStatus.COMPLETED;

    /**
     * Optional error message if OCR failed
     */
    @Column(name = "col_error_message", length = 2000)
    private String errorMessage;

    /**
     * Word count of the page
     */
    @Column(name = "col_word_count")
    private int wordCount;
}
