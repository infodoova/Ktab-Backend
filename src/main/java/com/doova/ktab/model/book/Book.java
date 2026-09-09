package com.doova.ktab.model.book;

import com.doova.ktab.enums.book.BookSource;
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
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_book_source", nullable = false)
    private BookSource bookSource = BookSource.AUTHOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_library_organization_id")
    private LibraryOrganization libraryOrganization;

    @Column(name = "col_custom_author_name")
    private String customAuthorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_uploader_id")
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

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookReview> reviews = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_genre_id")
    private MainGenre mainGenre;

    @Enumerated(EnumType.STRING)
    @Column(name = "col_ocr_status")
    @ColumnDefault("'PENDING'")
    private OcrStatus ocrStatus = OcrStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_genre_id")
    private SubGenre subGenre;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookPage> sections = new HashSet<>();

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
