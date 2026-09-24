package com.doova.ktab.model.book;

import com.doova.ktab.enums.book.ImageQuality;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SpreadSide;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "tbl_book_pages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_book_pages_book_page", columnNames = {"col_book_id", "col_page_number"})
        },
        indexes = {
                @Index(name = "idx_book_pages_book", columnList = "col_book_id"),
                @Index(name = "idx_book_pages_page", columnList = "col_page_number"),
                @Index(name = "idx_book_pages_source_pdf", columnList = "col_book_id, col_source_pdf_page, col_spread_side"),
                @Index(name = "idx_book_pages_section", columnList = "col_book_id, col_section_id")
        }
)
@Getter
@Setter
public class BookPage extends BaseEntity {

    /**
     * Parent book
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "col_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_book_pages_book"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Book book;

    /**
     * Book page number (1..N in reading order after spread splitting)
     */
    @Column(name = "col_page_number", nullable = false)
    private int pageNumber;

    /**
     * Original PDF page index (1-based) from which this page was derived
     */
    @Column(name = "col_source_pdf_page")
    private Integer sourcePdfPage;

    /**
     * Spread side if derived from a split two-page spread (NONE, RIGHT, LEFT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "col_spread_side", nullable = false, length = 10)
    private SpreadSide spreadSide = SpreadSide.NONE;

    /**
     * Rotation degrees (0, 90, 180, 270) applied prior to OCR
     */
    @Column(name = "col_rotation_degrees", nullable = false)
    private short rotationDegrees = 0;

    /**
     * Rendering resolution in DPI
     */
    @Column(name = "col_render_dpi")
    private Short renderDpi;

    public void setRotationDegrees(int rotationDegrees) {
        this.rotationDegrees = (short) rotationDegrees;
    }

    public void setRenderDpi(Integer renderDpi) {
        this.renderDpi = renderDpi != null ? renderDpi.shortValue() : null;
    }

    /**
     * Image pixel dimensions
     */
    @Column(name = "col_image_width")
    private Integer imageWidth;

    @Column(name = "col_image_height")
    private Integer imageHeight;

    /**
     * Computed/reported image quality (GOOD, FAIR, POOR)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "col_image_quality", length = 10)
    private ImageQuality imageQuality;

    /**
     * Image metrics JSON (contrast, ink density, borderCropPx, etc.)
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "col_image_metrics", columnDefinition = "JSONB")
    private String imageMetrics;

    /**
     * Semantic kind of the page (COVER, TOC, BODY, BLANK, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "col_page_kind", nullable = false, length = 20)
    private PageKind pageKind = PageKind.UNKNOWN;

    /**
     * Printed page label as extracted from the page (e.g. "45", "٤٥", "ج")
     */
    @Column(name = "col_printed_page_label", length = 20)
    private String printedPageLabel;

    /**
     * Running header extracted from the top of the page
     */
    @Column(name = "col_running_header", length = 500)
    private String runningHeader;

    /**
     * Headings detected on this page (JSON array)
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "col_headings", columnDefinition = "JSONB")
    private String headings;

    /**
     * Raw OCR result in Markdown format (transcribed body text, excluding headers/footers)
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "col_markdown_content", columnDefinition = "TEXT")
    private String markdownContent;

    /**
     * Footnotes extracted from the page in Markdown format
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "col_footnotes_markdown", columnDefinition = "TEXT")
    private String footnotesMarkdown;

    /**
     * Cleaned/harmonized Markdown text (raw markdownContent remains immutable)
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "col_markdown_clean", columnDefinition = "TEXT")
    private String markdownClean;

    /**
     * Page boundary flags
     */
    @Column(name = "col_starts_mid_sentence")
    private Boolean startsMidSentence;

    @Column(name = "col_ends_mid_sentence")
    private Boolean endsMidSentence;

    /**
     * Associated hierarchical section
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_section_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private BookSection section;

    /**
     * OCR status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "col_ocr_status", nullable = false, length = 20)
    private OcrStatus status = OcrStatus.COMPLETED;

    /**
     * Quality flags JSON (e.g. ["REPETITION", "TRUNCATED", "ROTATED"])
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "col_quality_flags", columnDefinition = "JSONB")
    private String qualityFlags;

    /**
     * Model and prompt version used for OCR
     */
    @Column(name = "col_ocr_model", length = 100)
    private String ocrModel;

    @Column(name = "col_prompt_version", length = 20)
    private String promptVersion;

    /**
     * Set only for pages synthesized from a Studio chapter's projected text. Paired with
     * {@link #chapterPageOrdinal} to make re-projection an idempotent upsert instead of a
     * rebuild. See docs/ocr_engine_v3.md, Phase 3.4/3.6.
     */
    @Column(name = "col_external_chapter_id", length = 64)
    private String externalChapterId;

    /** This page's position (0-based) within its Studio chapter's synthetic pagination. */
    @Column(name = "col_chapter_page_ordinal")
    private Integer chapterPageOrdinal;

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
