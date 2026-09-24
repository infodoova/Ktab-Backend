package com.doova.ktab.model.book;

import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.enums.book.PdfType;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.base.BaseEntity;
import com.doova.ktab.model.genre.MainGenre;
import com.doova.ktab.model.genre.SubGenre;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.event.listener.BookPublishDateListener;
import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_books")
@FilterDef(
        name = "publishedFilter",
        parameters = @ParamDef(name = "status", type = String.class)
)
@Filter(
        name = "publishedFilter",
        condition = "col_status = :status"
)
@EntityListeners(BookPublishDateListener.class)
public class Book extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_author_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_book_source", nullable = false)
    private BookSource bookSource = BookSource.AUTHOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_library_organization_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LibraryOrganization libraryOrganization;

    @Column(name = "col_custom_author_name")
    private String customAuthorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_uploader_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User uploader;

    @NotBlank(message = "{validation.book.title.required}")
    @Size(max = 255, message = "{validation.book.title.size}")
    @Column(name = "col_title", nullable = false)
    private String title;

    @Column(name = "col_description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "col_language")
    private String language;

    @Column(name = "col_age_range_min")
    private Integer ageRangeMin;

    @Column(name = "col_age_range_max")
    private Integer ageRangeMax;

    @Column(name = "col_page_count")
    private Integer pageCount;

    @Column(name = "col_has_audio")
    @ColumnDefault("false")
    private Boolean hasAudio = false;

    @Column(name = "col_average_rating", precision = 3, scale = 2)
    @ColumnDefault("0.00")
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "col_total_reviews")
    @ColumnDefault("0")
    private Integer totalReviews = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_status", nullable = false)
    @ColumnDefault("'DRAFT'")
    private BookStatus status = BookStatus.DRAFT;

    @Column(name = "col_publish_date")
    private Instant publishDate;

    @Column(name = "col_submitted_at")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_reviewed_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User reviewedBy;

    @Column(name = "col_reviewed_at")
    private Instant reviewedAt;

    @Column(name = "col_review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookReview> reviews = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_genre_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private MainGenre mainGenre;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_ocr_status")
    @ColumnDefault("'PENDING'")
    private OcrStatus ocrStatus = OcrStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_pdf_type", length = 20)
    private PdfType pdfType;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_ingestion_route", length = 20)
    private IngestionRoute ingestionRoute;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "col_pdf_classification", columnDefinition = "JSONB")
    private String pdfClassification;

    @Column(name = "col_classifier_version", length = 10)
    private String classifierVersion;

    @Column(name = "col_ingestion_route_locked", nullable = false)
    @ColumnDefault("false")
    private boolean ingestionRouteLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_genre_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private SubGenre subGenre;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookPage> sections = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "col_reading_direction", nullable = false, length = 3)
    @ColumnDefault("'RTL'")
    private com.doova.ktab.enums.book.ReadingDirection readingDirection = com.doova.ktab.enums.book.ReadingDirection.RTL;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_pagination_mode", length = 10)
    private com.doova.ktab.enums.book.PaginationMode paginationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_structure_status", length = 20)
    @ColumnDefault("'NONE'")
    private com.doova.ktab.enums.book.StructureStatus structureStatus = com.doova.ktab.enums.book.StructureStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_structure_source", length = 20)
    private com.doova.ktab.enums.book.StructureSource structureSource;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "col_toc_raw", columnDefinition = "JSONB")
    private String tocRaw;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<BookSection> bookSections = new HashSet<>();

    public void addBookSection(BookSection bookSection) {
        if (bookSection != null) {
            bookSection.setBook(this);
            bookSections.add(bookSection);
        }
    }

    public void removeBookSection(BookSection bookSection) {
        if (bookSection != null && bookSections.remove(bookSection)) {
            bookSection.setBook(null);
        }
    }

    public void addSection(BookPage section) {
        if (section != null) {
            section.setBook(this);
            sections.add(section);
        }
    }

    public void removeSection(BookPage section) {
        if (section != null && sections.remove(section)) {
            section.setBook(null);
        }
    }

    public void addReview(BookReview review) {
        if (review == null) return;

        reviews.add(review);
        review.setBook(this);
    }

    public void removeReview(BookReview review) {
        if (review == null) return;

        if (reviews.remove(review)) {
            review.setBook(null);
        }
    }
}
