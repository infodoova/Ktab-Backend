package com.doova.ktab.model.book;

import com.doova.ktab.model.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import com.doova.ktab.model.base.BaseEntity;

import java.time.Instant;

/**
 * Represents a user's entry in their "My Library" or favorites list for a book.
 * This entity tracks personal reader state for a book.
 */
@EqualsAndHashCode(callSuper = true, exclude = {"book", "user"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "tbl_book_library_entries", uniqueConstraints = {
        // Constraint: A user can only have one library entry for a single book.
        @UniqueConstraint(name = "uq_book_library_entries_user_book", columnNames = {"col_user_id", "col_book_id"})})
public class BookLibraryEntry extends BaseEntity {

    // --- Foreign Key: Reader (User) ---
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_user_id", nullable = false)
    private User user;

    // --- Foreign Key: Book ---
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "col_book_id", nullable = false)
    private Book book;

    // --- Core Library Fields ---

    @NotNull
    @Column(name = "col_is_favorite", nullable = false)
    @ColumnDefault("false")
    private Boolean isFavorite = false;

    /**
     * Specific timestamp when the book was added to the user's library.
     * This may often be the same as BaseEntity's createdAt field, but is included
     * for explicit schema matching.
     */
    @NotNull
    @Column(name = "col_added_at", nullable = false)
    private Instant addedAt = Instant.now();
}
