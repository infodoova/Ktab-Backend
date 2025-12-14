package com.doova.ktab.model.book;

import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.doova.ktab.model.base.BaseEntity;

/**
 * Represents a reader's review and rating for a specific book.
 * The data from this entity feeds into the analytics (e.g., Book.averageRating).
 */
@EqualsAndHashCode(callSuper = true, exclude = {"book", "reader"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "tbl_book_reviews", uniqueConstraints = {
        // Constraint: A single user can only submit one review per book.
        @UniqueConstraint(name = "uq_book_reviews_reader_book", columnNames = {"col_book_id", "col_reader_id"})})
public class BookReview extends BaseEntity {

    // --- Foreign Key: Book ---
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_book_id", nullable = false)
    private Book book;

    // --- Foreign Key: Reader (User) ---
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_reader_id", nullable = false)
    private User reader;

    // --- Core Review Fields ---

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Column(name = "col_rating", nullable = false)
    private Integer rating;

    @Column(name = "col_comment", columnDefinition = "TEXT")
    private String comment; // Textual review comment

}