package com.doova.ktab.model.book;

import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.doova.ktab.model.base.BaseEntity;

/**
 * Represents a reader's review and rating for a specific book.
 * The data from this entity feeds into the analytics (e.g., Book.averageRating).
 */
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
    @NotNull(message = "{validation.book.required}")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_book_id", nullable = false)
    private Book book;

    // --- Foreign Key: Reader (User) ---
    @NotNull(message = "{validation.user.required}")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_reader_id", nullable = false)
    private User reader;

    // --- Core Review Fields ---

    @NotNull(message = "{validation.rating.required}")
    @Min(value = 1, message = "{validation.rating.min}")
    @Max(value = 5, message = "{validation.rating.max}")
    @Column(name = "col_rating", nullable = false)
    private Integer rating;

    @Size(max = 1000, message = "{validation.review.comment.size}")
    @Column(name = "col_comment", columnDefinition = "TEXT")
    private String comment; // Textual review comment

}